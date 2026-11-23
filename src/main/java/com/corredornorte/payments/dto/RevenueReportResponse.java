package com.corredornorte.payments.dto;

import com.corredornorte.payments.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reporte consolidado de ingresos y recaudación por rango de fechas (Java 21 record).
 */
public record RevenueReportResponse(
    OffsetDateTime from,
    OffsetDateTime to,
    BigDecimal totalPaid,
    BigDecimal totalPending,
    BigDecimal totalRejected,
    Long totalTransactions,
    List<MethodRevenueSummary> breakdownByMethod
) {
    public record MethodRevenueSummary(
        PaymentMethod paymentMethod,
        Long transactionCount,
        BigDecimal paidAmount
    ) {}
}
