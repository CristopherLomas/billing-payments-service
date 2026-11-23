package com.corredornorte.payments.dto;

import com.corredornorte.payments.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Reporte de conciliación diaria de caja y comprobantes (Java 21 record).
 */
public record ReconciliationResponse(
    LocalDate date,
    List<PaymentResponse> payments,
    Long totalTransactions,
    BigDecimal totalPaidAmount,
    BigDecimal totalPendingAmount,
    BigDecimal totalRejectedAmount,
    List<MethodReconciliationSummary> summaryByMethod
) {
    public record MethodReconciliationSummary(
        PaymentMethod paymentMethod,
        Long transactionCount,
        BigDecimal totalAmount
    ) {}
}
