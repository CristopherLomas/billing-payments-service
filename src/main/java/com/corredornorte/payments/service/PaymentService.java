package com.corredornorte.payments.service;

import com.corredornorte.payments.dto.CreatePaymentRequest;
import com.corredornorte.payments.dto.PaymentAuditLogResponse;
import com.corredornorte.payments.dto.PaymentResponse;
import com.corredornorte.payments.entity.Payment;
import com.corredornorte.payments.entity.PaymentAuditLog;
import com.corredornorte.payments.entity.PaymentMethod;
import com.corredornorte.payments.entity.PaymentStatus;
import com.corredornorte.payments.exception.DuplicateReferenceException;
import com.corredornorte.payments.exception.InvalidPaymentRequestException;
import com.corredornorte.payments.exception.InvalidPaymentStateException;
import com.corredornorte.payments.exception.PaymentNotFoundException;
import com.corredornorte.payments.exception.UnauthorizedPaymentAccessException;
import com.corredornorte.payments.integration.ExternalBookingGateway;
import com.corredornorte.payments.repository.PaymentAuditLogRepository;
import com.corredornorte.payments.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final List<PaymentStatus> PENDING_STATUSES = List.of(
            PaymentStatus.PENDING_VERIFICATION,
            PaymentStatus.PENDING_CASH
    );

    private final PaymentRepository paymentRepository;
    private final PaymentAuditLogRepository paymentAuditLogRepository;
    private final ExternalBookingGateway externalBookingGateway;

    /**
     * Registra un nuevo pago en el sistema.
     * Si es transferencia o Deuna, valida y normaliza el comprobante único.
     * Si es efectivo, lo marca como PENDING_CASH sin comprobante.
     */
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, String userId) {
        log.info("Iniciando creación de pago para reservationId={}, method={}, user={}",
                request.reservationId(), request.paymentMethod(), userId);

        String referenceToSave = null;
        PaymentStatus initialStatus;

        if (request.paymentMethod() == PaymentMethod.BANK_TRANSFER || request.paymentMethod() == PaymentMethod.DEUNA) {
            if (request.referenceNumber() == null || request.referenceNumber().trim().isEmpty()) {
                throw new InvalidPaymentRequestException("El número de comprobante/referencia es obligatorio para " + request.paymentMethod());
            }

            referenceToSave = request.referenceNumber().trim().toUpperCase();

            if (paymentRepository.existsByReferenceNumber(referenceToSave)) {
                throw new DuplicateReferenceException(referenceToSave);
            }

            initialStatus = PaymentStatus.PENDING_VERIFICATION;
        } else {
            // CASH
            referenceToSave = null;
            initialStatus = PaymentStatus.PENDING_CASH;
        }

        Payment payment = Payment.builder()
                .reservationId(request.reservationId())
                .tripId(request.tripId())
                .userId(userId)
                .amount(request.amount().setScale(2, RoundingMode.HALF_UP))
                .paymentMethod(request.paymentMethod())
                .referenceNumber(referenceToSave)
                .seatNumber(request.seatNumber())
                .status(initialStatus)
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        PaymentAuditLog initialAudit = PaymentAuditLog.builder()
                .paymentId(savedPayment.getId())
                .previousStatus(null)
                .newStatus(initialStatus)
                .changedBy(userId)
                .notes(request.paymentMethod() == PaymentMethod.CASH
                        ? "Registro de pago pendiente en efectivo al abordar"
                        : "Registro de comprobante bancario para verificación")
                .changedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        paymentAuditLogRepository.save(initialAudit);

        log.info("Pago registrado exitosamente con id={}, status={}", savedPayment.getId(), savedPayment.getStatus());
        return PaymentResponse.fromEntity(savedPayment);
    }

    /**
     * Consulta un pago por ID validando que el solicitante sea el propietario o un Administrador.
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID id, String requesterId, boolean isAdmin) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        if (!isAdmin && !payment.getUserId().equals(requesterId)) {
            throw new UnauthorizedPaymentAccessException("No tiene permisos para consultar este comprobante de pago.");
        }

        return PaymentResponse.fromEntity(payment);
    }

    /**
     * Obtiene el historial paginado de pagos de un usuario específico.
     */
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getMyPayments(String userId, Pageable pageable) {
        return paymentRepository.findByUserId(userId, pageable)
                .map(PaymentResponse::fromEntity);
    }

    /**
     * Bandeja de entrada para el Administrador con pagos pendientes (FIFO - más antiguos primero).
     */
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPendingPayments(PaymentMethod methodFilter, Pageable pageable) {
        Pageable fifoPageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.ASC, "createdAt"));

        Page<Payment> page;
        if (methodFilter != null) {
            page = paymentRepository.findByStatusInAndPaymentMethod(PENDING_STATUSES, methodFilter, fifoPageable);
        } else {
            page = paymentRepository.findByStatusIn(PENDING_STATUSES, fifoPageable);
        }

        return page.map(PaymentResponse::fromEntity);
    }

    /**
     * Aprueba un pago en estado PENDING_VERIFICATION o PENDING_CASH.
     * Actualiza el estado a PAID, audita la acción y notifica al servicio de QR para emisión.
     */
    @Transactional
    public PaymentResponse approvePayment(UUID id, String adminId, String notes) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.REJECTED) {
            throw new InvalidPaymentStateException(id, payment.getStatus(),
                    "El pago ya se encuentra finalizado (" + payment.getStatus() + ") y no puede ser aprobado nuevamente.");
        }

        PaymentStatus previousStatus = payment.getStatus();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        payment.setStatus(PaymentStatus.PAID);
        payment.setReviewedBy(adminId);
        payment.setReviewedAt(now);

        Payment updatedPayment = paymentRepository.save(payment);

        PaymentAuditLog auditLog = PaymentAuditLog.builder()
                .paymentId(updatedPayment.getId())
                .previousStatus(previousStatus)
                .newStatus(PaymentStatus.PAID)
                .changedBy(adminId)
                .notes(notes != null && !notes.isBlank() ? notes.trim() : "Pago aprobado por administración")
                .changedAt(now)
                .build();

        paymentAuditLogRepository.save(auditLog);

        externalBookingGateway.notifyPaymentApproved(
                updatedPayment.getReservationId(),
                updatedPayment.getTripId(),
                updatedPayment.getId(),
                updatedPayment.getUserId()
        );

        log.info("Pago {} aprobado exitosamente por {}", id, adminId);
        return PaymentResponse.fromEntity(updatedPayment);
    }

    /**
     * Rechaza un comprobante de pago en estado pendiente.
     * Actualiza el estado a REJECTED, audita la acción y notifica al servicio de reservas para liberar el asiento.
     */
    @Transactional
    public PaymentResponse rejectPayment(UUID id, String adminId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidPaymentRequestException("El motivo del rechazo es obligatorio.");
        }

        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.REJECTED) {
            throw new InvalidPaymentStateException(id, payment.getStatus(),
                    "El pago ya se encuentra finalizado (" + payment.getStatus() + ") y no puede ser rechazado nuevamente.");
        }

        PaymentStatus previousStatus = payment.getStatus();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        payment.setStatus(PaymentStatus.REJECTED);
        payment.setRejectionReason(reason.trim());
        payment.setReviewedBy(adminId);
        payment.setReviewedAt(now);

        Payment updatedPayment = paymentRepository.save(payment);

        PaymentAuditLog auditLog = PaymentAuditLog.builder()
                .paymentId(updatedPayment.getId())
                .previousStatus(previousStatus)
                .newStatus(PaymentStatus.REJECTED)
                .changedBy(adminId)
                .notes("Rechazo de comprobante: " + reason.trim())
                .changedAt(now)
                .build();

        paymentAuditLogRepository.save(auditLog);

        externalBookingGateway.notifyPaymentRejected(
                updatedPayment.getReservationId(),
                updatedPayment.getTripId(),
                updatedPayment.getId(),
                updatedPayment.getUserId(),
                updatedPayment.getSeatNumber(),
                reason.trim()
        );

        log.warn("Pago {} rechazado por {} - Motivo: {}", id, adminId, reason.trim());
        return PaymentResponse.fromEntity(updatedPayment);
    }

    /**
     * Obtiene la traza cronológica completa de auditoría para un pago.
     */
    @Transactional(readOnly = true)
    public List<PaymentAuditLogResponse> getAuditTrail(UUID paymentId) {
        if (!paymentRepository.existsById(paymentId)) {
            throw new PaymentNotFoundException(paymentId);
        }

        return paymentAuditLogRepository.findByPaymentIdOrderByChangedAtAsc(paymentId)
                .stream()
                .map(PaymentAuditLogResponse::fromEntity)
                .toList();
    }
}
