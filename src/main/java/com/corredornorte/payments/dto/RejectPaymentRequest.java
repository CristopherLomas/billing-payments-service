package com.corredornorte.payments.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Solicitud para rechazo de comprobante de pago por el Administrador.
 */
public record RejectPaymentRequest(
    @NotBlank(message = "El motivo de rechazo es obligatorio")
    String reason
) {}
