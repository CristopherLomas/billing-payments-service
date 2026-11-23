package com.corredornorte.payments.dto.report;

import com.corredornorte.payments.entity.PaymentMethod;

import java.math.BigDecimal;

/**
 * Record DTO para conciliación de pagos por fecha.
 */
public record DailyReconciliationDto(
    PaymentMethod paymentMethod,
    Long totalTransactions,
    BigDecimal totalCollected
) {}
