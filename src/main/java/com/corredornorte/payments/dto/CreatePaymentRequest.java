package com.corredornorte.payments.dto;

import com.corredornorte.payments.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Solicitud de registro de pago (Java 21 record).
 */
public record CreatePaymentRequest(
    @NotBlank(message = "El reservationId es obligatorio")
    String reservationId,

    @NotBlank(message = "El tripId es obligatorio")
    String tripId,

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor o igual a 0.01")
    BigDecimal amount,

    @NotNull(message = "El método de pago es obligatorio")
    PaymentMethod paymentMethod,

    String referenceNumber
) {}
