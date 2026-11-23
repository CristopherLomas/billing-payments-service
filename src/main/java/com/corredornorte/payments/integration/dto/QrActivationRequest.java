package com.corredornorte.payments.integration.dto;

import java.util.UUID;

/**
 * Payload para solicitar la emisión del boleto QR tras la aprobación del pago.
 */
public record QrActivationRequest(
    String reservationId,
    String tripId,
    UUID paymentId,
    String userId
) {}
