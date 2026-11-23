package com.corredornorte.payments.service;

import com.corredornorte.payments.dto.PaymentResponse;
import com.corredornorte.payments.dto.ReconciliationResponse;
import com.corredornorte.payments.dto.RevenueReportResponse;
import com.corredornorte.payments.dto.report.RevenueReportDto;
import com.corredornorte.payments.entity.Payment;
import com.corredornorte.payments.entity.PaymentMethod;
import com.corredornorte.payments.entity.PaymentStatus;
import com.corredornorte.payments.exception.InvalidPaymentRequestException;
import com.corredornorte.payments.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final PaymentRepository paymentRepository;

    /**
     * Genera el reporte de recaudación por rango de fechas UTC.
     * Consolida montos recaudados (PAID), pendientes, rechazados y desglose por método.
     */
    @Transactional(readOnly = true)
    public RevenueReportResponse getRevenueReport(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            throw new InvalidPaymentRequestException("Las fechas de inicio 'from' y fin 'to' son obligatorias.");
        }
        if (from.isAfter(to)) {
            throw new InvalidPaymentRequestException("La fecha de inicio 'from' debe ser anterior o igual a la fecha 'to'.");
        }

        List<RevenueReportDto> aggregations = paymentRepository.getRevenueReportByDateRange(from, to);

        BigDecimal totalPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPending = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRejected = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        long totalTransactions = 0;

        for (RevenueReportDto dto : aggregations) {
            totalTransactions += dto.transactionCount();
            BigDecimal amount = dto.totalAmount() != null ? dto.totalAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            if (dto.status() == PaymentStatus.PAID) {
                totalPaid = totalPaid.add(amount);
            } else if (dto.status() == PaymentStatus.PENDING_VERIFICATION || dto.status() == PaymentStatus.PENDING_CASH) {
                totalPending = totalPending.add(amount);
            } else if (dto.status() == PaymentStatus.REJECTED) {
                totalRejected = totalRejected.add(amount);
            }
        }

        Map<PaymentMethod, List<RevenueReportDto>> byMethod = aggregations.stream()
                .collect(Collectors.groupingBy(RevenueReportDto::paymentMethod));

        List<RevenueReportResponse.MethodRevenueSummary> methodSummaries = Arrays.stream(PaymentMethod.values())
                .map(method -> {
                    List<RevenueReportDto> list = byMethod.getOrDefault(method, List.of());
                    long count = list.stream().mapToLong(RevenueReportDto::transactionCount).sum();
                    BigDecimal paid = list.stream()
                            .filter(d -> d.status() == PaymentStatus.PAID)
                            .map(RevenueReportDto::totalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(2, RoundingMode.HALF_UP);
                    return new RevenueReportResponse.MethodRevenueSummary(method, count, paid);
                })
                .toList();

        return new RevenueReportResponse(
                from,
                to,
                totalPaid,
                totalPending,
                totalRejected,
                totalTransactions,
                methodSummaries
        );
    }

    /**
     * Genera el reporte de conciliación para un día específico (cuadre de caja).
     * Devuelve el detalle de pagos y sumatorias de auditoría financiera.
     */
    @Transactional(readOnly = true)
    public ReconciliationResponse getReconciliationReport(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now(ZoneOffset.UTC);

        OffsetDateTime startOfDay = targetDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endOfDay = targetDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1);

        List<Payment> payments = paymentRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(startOfDay, endOfDay);
        List<PaymentResponse> paymentResponses = payments.stream()
                .map(PaymentResponse::fromEntity)
                .toList();

        BigDecimal totalPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPending = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRejected = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (Payment p : payments) {
            BigDecimal amt = p.getAmount() != null ? p.getAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (p.getStatus() == PaymentStatus.PAID) {
                totalPaid = totalPaid.add(amt);
            } else if (p.getStatus() == PaymentStatus.PENDING_VERIFICATION || p.getStatus() == PaymentStatus.PENDING_CASH) {
                totalPending = totalPending.add(amt);
            } else if (p.getStatus() == PaymentStatus.REJECTED) {
                totalRejected = totalRejected.add(amt);
            }
        }

        Map<PaymentMethod, List<Payment>> byMethod = payments.stream()
                .collect(Collectors.groupingBy(Payment::getPaymentMethod));

        List<ReconciliationResponse.MethodReconciliationSummary> methodSummaries = Arrays.stream(PaymentMethod.values())
                .map(method -> {
                    List<Payment> list = byMethod.getOrDefault(method, List.of());
                    long count = list.size();
                    BigDecimal paidAmount = list.stream()
                            .filter(p -> p.getStatus() == PaymentStatus.PAID)
                            .map(Payment::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(2, RoundingMode.HALF_UP);
                    return new ReconciliationResponse.MethodReconciliationSummary(method, count, paidAmount);
                })
                .toList();

        return new ReconciliationResponse(
                targetDate,
                paymentResponses,
                (long) payments.size(),
                totalPaid,
                totalPending,
                totalRejected,
                methodSummaries
        );
    }
}
