package com.corredornorte.payments.dto.report;

import com.corredornorte.payments.entity.PaymentMethod;
import com.corredornorte.payments.entity.PaymentStatus;

import java.math.BigDecimal;

/**
 * Record DTO para reportes de recaudación agrupados por método y estado.
 */
public record RevenueReportDto(
    PaymentMethod paymentMethod,
    PaymentStatus status,
    Long transactionCount,
    BigDecimal totalAmount
) {}
