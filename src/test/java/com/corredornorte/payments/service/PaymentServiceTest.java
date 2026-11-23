package com.corredornorte.payments.service;

import com.corredornorte.payments.dto.CreatePaymentRequest;
import com.corredornorte.payments.dto.PaymentResponse;
import com.corredornorte.payments.entity.Payment;
import com.corredornorte.payments.entity.PaymentAuditLog;
import com.corredornorte.payments.entity.PaymentMethod;
import com.corredornorte.payments.entity.PaymentStatus;
import com.corredornorte.payments.exception.DuplicateReferenceException;
import com.corredornorte.payments.exception.InvalidPaymentRequestException;
import com.corredornorte.payments.exception.InvalidPaymentStateException;
import com.corredornorte.payments.integration.ExternalBookingGateway;
import com.corredornorte.payments.repository.PaymentAuditLogRepository;
import com.corredornorte.payments.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentAuditLogRepository paymentAuditLogRepository;

    @Mock
    private ExternalBookingGateway externalBookingGateway;

    @InjectMocks
    private PaymentService paymentService;

    private Payment pendingPayment;
    private Payment paidPayment;
    private Payment rejectedPayment;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        pendingPayment = Payment.builder()
                .id(UUID.randomUUID())
                .reservationId("RES-1001")
                .tripId("TRIP-500")
                .userId("USR-888")
                .amount(new BigDecimal("12.50"))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .referenceNumber("REF-TRANSFER-123")
                .seatNumber(14)
                .status(PaymentStatus.PENDING_VERIFICATION)
                .createdAt(now)
                .updatedAt(now)
                .build();

        paidPayment = Payment.builder()
                .id(UUID.randomUUID())
                .reservationId("RES-1002")
                .tripId("TRIP-500")
                .userId("USR-888")
                .amount(new BigDecimal("15.00"))
                .paymentMethod(PaymentMethod.DEUNA)
                .referenceNumber("REF-DEUNA-456")
                .status(PaymentStatus.PAID)
                .reviewedBy("admin-1")
                .reviewedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        rejectedPayment = Payment.builder()
                .id(UUID.randomUUID())
                .reservationId("RES-1003")
                .tripId("TRIP-500")
                .userId("USR-888")
                .amount(new BigDecimal("8.00"))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .referenceNumber("REF-INVALID-999")
                .status(PaymentStatus.REJECTED)
                .rejectionReason("Comprobante ilegible")
                .reviewedBy("admin-1")
                .reviewedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Nested
    @DisplayName("Creación de pagos y validación de comprobantes")
    class CreatePaymentTests {

        @Test
        @DisplayName("Creación exitosa de pago por DEUNA (estado PENDING_VERIFICATION + log de auditoría)")
        void createPaymentDeunaSuccess() {
            CreatePaymentRequest request = new CreatePaymentRequest(
                    "RES-2000",
                    "TRIP-500",
                    new BigDecimal("10.50"),
                    PaymentMethod.DEUNA,
                    "  deuna-pay-777  "
            );

            when(paymentRepository.existsByReferenceNumber("DEUNA-PAY-777")).thenReturn(false);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            PaymentResponse response = paymentService.createPayment(request, "USR-888");

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PaymentStatus.PENDING_VERIFICATION);
            assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.DEUNA);
            assertThat(response.referenceNumber()).isEqualTo("DEUNA-PAY-777");

            // Verifica inserción en audit log
            ArgumentCaptor<PaymentAuditLog> logCaptor = ArgumentCaptor.forClass(PaymentAuditLog.class);
            verify(paymentAuditLogRepository).save(logCaptor.capture());
            PaymentAuditLog capturedLog = logCaptor.getValue();
            assertThat(capturedLog.getPreviousStatus()).isNull();
            assertThat(capturedLog.getNewStatus()).isEqualTo(PaymentStatus.PENDING_VERIFICATION);
            assertThat(capturedLog.getChangedBy()).isEqualTo("USR-888");
        }

        @Test
        @DisplayName("Creación exitosa de pago por CASH (estado PENDING_CASH, referenceNumber null)")
        void createPaymentCashSuccess() {
            CreatePaymentRequest request = new CreatePaymentRequest(
                    "RES-2005",
                    "TRIP-500",
                    new BigDecimal("4.50"),
                    PaymentMethod.CASH,
                    null
            );

            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            PaymentResponse response = paymentService.createPayment(request, "USR-999");

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PaymentStatus.PENDING_CASH);
            assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CASH);
            assertThat(response.referenceNumber()).isNull();

            // Verifica que nunca se consulta comprobante para efectivo
            verify(paymentRepository, never()).existsByReferenceNumber(anyString());

            // Verifica inserción en audit log
            ArgumentCaptor<PaymentAuditLog> logCaptor = ArgumentCaptor.forClass(PaymentAuditLog.class);
            verify(paymentAuditLogRepository).save(logCaptor.capture());
            PaymentAuditLog capturedLog = logCaptor.getValue();
            assertThat(capturedLog.getPreviousStatus()).isNull();
            assertThat(capturedLog.getNewStatus()).isEqualTo(PaymentStatus.PENDING_CASH);
            assertThat(capturedLog.getChangedBy()).isEqualTo("USR-999");
        }

        @Test
        @DisplayName("Error 409 DuplicateReferenceException al repetir número de comprobante")
        void rejectDuplicateReferenceNumber() {
            CreatePaymentRequest request = new CreatePaymentRequest(
                    "RES-2001",
                    "TRIP-500",
                    new BigDecimal("10.50"),
                    PaymentMethod.DEUNA,
                    "REF-EXISTING-999"
            );

            when(paymentRepository.existsByReferenceNumber("REF-EXISTING-999")).thenReturn(true);

            assertThatThrownBy(() -> paymentService.createPayment(request, "USR-888"))
                    .isInstanceOf(DuplicateReferenceException.class)
                    .hasMessageContaining("REF-EXISTING-999");

            verify(paymentRepository, never()).save(any());
            verify(paymentAuditLogRepository, never()).save(any());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Error 400 InvalidPaymentRequestException al enviar BANK_TRANSFER sin comprobante")
        void rejectBankTransferWithoutReference(String invalidReference) {
            CreatePaymentRequest request = new CreatePaymentRequest(
                    "RES-2002",
                    "TRIP-500",
                    new BigDecimal("12.00"),
                    PaymentMethod.BANK_TRANSFER,
                    invalidReference
            );

            assertThatThrownBy(() -> paymentService.createPayment(request, "USR-888"))
                    .isInstanceOf(InvalidPaymentRequestException.class)
                    .hasMessageContaining("El número de comprobante/referencia es obligatorio para BANK_TRANSFER");

            verify(paymentRepository, never()).existsByReferenceNumber(anyString());
            verify(paymentRepository, never()).save(any());
            verify(paymentAuditLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Debe registrar pago por transferencia bancaria y normalizar comprobante a mayúsculas")
        void createPaymentBankTransferSuccess() {
            CreatePaymentRequest request = new CreatePaymentRequest(
                    "RES-2000",
                    "TRIP-500",
                    new BigDecimal("10.50"),
                    PaymentMethod.BANK_TRANSFER,
                    "  ref-banco-001  "
            );

            when(paymentRepository.existsByReferenceNumber("REF-BANCO-001")).thenReturn(false);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            PaymentResponse response = paymentService.createPayment(request, "USR-888");

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PaymentStatus.PENDING_VERIFICATION);
            assertThat(response.referenceNumber()).isEqualTo("REF-BANCO-001");
        }

        @Test
        @DisplayName("Creación exitosa de pago incluyendo seatNumber para integración con Trips Service")
        void createPaymentWithSeatNumberSuccess() {
            CreatePaymentRequest request = new CreatePaymentRequest(
                    "RES-3000",
                    "4c988b7b-a9bd-45cf-b3e8-26380bf4198b",
                    new BigDecimal("10.50"),
                    PaymentMethod.DEUNA,
                    "DEUNA-SEAT-14",
                    14
            );

            when(paymentRepository.existsByReferenceNumber("DEUNA-SEAT-14")).thenReturn(false);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            PaymentResponse response = paymentService.createPayment(request, "USR-888");

            assertThat(response).isNotNull();
            assertThat(response.seatNumber()).isEqualTo(14);
        }
    }

    @Nested
    @DisplayName("Máquina de estados y aprobación/rechazo")
    class StateMachineTests {

        @Test
        @DisplayName("Aprobación exitosa: pasa a PAID, guarda reviewedBy, inserta log de auditoría y notifica externalBookingGateway.notifyPaymentApproved")
        void approvePaymentSuccess() {
            when(paymentRepository.findById(pendingPayment.getId())).thenReturn(Optional.of(pendingPayment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

            PaymentResponse response = paymentService.approvePayment(pendingPayment.getId(), "admin-super", "Comprobante verificado con Pichincha");

            assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
            assertThat(response.reviewedBy()).isEqualTo("admin-super");

            // Verifica inserción de auditoría
            ArgumentCaptor<PaymentAuditLog> logCaptor = ArgumentCaptor.forClass(PaymentAuditLog.class);
            verify(paymentAuditLogRepository).save(logCaptor.capture());
            PaymentAuditLog log = logCaptor.getValue();
            assertThat(log.getPreviousStatus()).isEqualTo(PaymentStatus.PENDING_VERIFICATION);
            assertThat(log.getNewStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(log.getChangedBy()).isEqualTo("admin-super");

            // Verifica notificación externa
            verify(externalBookingGateway).notifyPaymentApproved(
                    pendingPayment.getReservationId(),
                    pendingPayment.getTripId(),
                    pendingPayment.getId(),
                    pendingPayment.getUserId()
            );
        }

        @Test
        @DisplayName("Rechazo exitoso: pasa a REJECTED, guarda motivo, inserta log de auditoría y notifica externalBookingGateway.notifyPaymentRejected")
        void rejectPaymentSuccess() {
            when(paymentRepository.findById(pendingPayment.getId())).thenReturn(Optional.of(pendingPayment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

            PaymentResponse response = paymentService.rejectPayment(pendingPayment.getId(), "admin-super", "Número de cuenta no coincide");

            assertThat(response.status()).isEqualTo(PaymentStatus.REJECTED);
            assertThat(response.rejectionReason()).isEqualTo("Número de cuenta no coincide");

            // Verifica inserción de auditoría
            ArgumentCaptor<PaymentAuditLog> logCaptor = ArgumentCaptor.forClass(PaymentAuditLog.class);
            verify(paymentAuditLogRepository).save(logCaptor.capture());
            PaymentAuditLog log = logCaptor.getValue();
            assertThat(log.getPreviousStatus()).isEqualTo(PaymentStatus.PENDING_VERIFICATION);
            assertThat(log.getNewStatus()).isEqualTo(PaymentStatus.REJECTED);

            // Verifica notificación externa para liberar asiento
            verify(externalBookingGateway).notifyPaymentRejected(
                    pendingPayment.getReservationId(),
                    pendingPayment.getTripId(),
                    pendingPayment.getId(),
                    pendingPayment.getUserId(),
                    pendingPayment.getSeatNumber(),
                    "Número de cuenta no coincide"
            );
        }

        @Test
        @DisplayName("Error 409 InvalidPaymentStateException al intentar aprobar un pago que ya está PAID")
        void cannotApproveAlreadyPaidPayment() {
            when(paymentRepository.findById(paidPayment.getId())).thenReturn(Optional.of(paidPayment));

            assertThatThrownBy(() -> paymentService.approvePayment(paidPayment.getId(), "admin-1", "Aprobar de nuevo"))
                    .isInstanceOf(InvalidPaymentStateException.class)
                    .hasMessageContaining("PAID");

            verify(externalBookingGateway, never()).notifyPaymentApproved(anyString(), anyString(), any(), anyString());
            verify(paymentAuditLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Error 409 InvalidPaymentStateException al intentar aprobar un pago que ya está REJECTED")
        void cannotApproveAlreadyRejectedPayment() {
            when(paymentRepository.findById(rejectedPayment.getId())).thenReturn(Optional.of(rejectedPayment));

            assertThatThrownBy(() -> paymentService.approvePayment(rejectedPayment.getId(), "admin-1", "Aprobar rechazado"))
                    .isInstanceOf(InvalidPaymentStateException.class)
                    .hasMessageContaining("REJECTED");

            verify(externalBookingGateway, never()).notifyPaymentApproved(anyString(), anyString(), any(), anyString());
            verify(paymentAuditLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Error 409 InvalidPaymentStateException al intentar rechazar un pago que ya está PAID")
        void cannotRejectAlreadyPaidPayment() {
            when(paymentRepository.findById(paidPayment.getId())).thenReturn(Optional.of(paidPayment));

            assertThatThrownBy(() -> paymentService.rejectPayment(paidPayment.getId(), "admin-1", "Rechazar pagado"))
                    .isInstanceOf(InvalidPaymentStateException.class)
                    .hasMessageContaining("PAID");

            verify(externalBookingGateway, never()).notifyPaymentRejected(anyString(), anyString(), any(), any(), any(), anyString());
            verify(paymentAuditLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Error 409 InvalidPaymentStateException al intentar rechazar un pago que ya está REJECTED")
        void cannotRejectAlreadyRejectedPayment() {
            when(paymentRepository.findById(rejectedPayment.getId())).thenReturn(Optional.of(rejectedPayment));

            assertThatThrownBy(() -> paymentService.rejectPayment(rejectedPayment.getId(), "admin-1", "Rechazar rechazado"))
                    .isInstanceOf(InvalidPaymentStateException.class)
                    .hasMessageContaining("REJECTED");

            verify(externalBookingGateway, never()).notifyPaymentRejected(anyString(), anyString(), any(), any(), any(), anyString());
            verify(paymentAuditLogRepository, never()).save(any());
        }
    }
}
