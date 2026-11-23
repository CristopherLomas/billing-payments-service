package com.corredornorte.payments.dto;

/**
 * Solicitud para aprobación de comprobante de pago por el Administrador.
 */
public record ApprovePaymentRequest(
    String notes
) {}
