package com.corredornorte.payments.integration.dto;

import java.util.UUID;

/**
 * Payload para solicitar la liberación del asiento tras el rechazo del comprobante de pago.
 */
public record SeatReleaseRequest(
    String reservationId,
    String tripId,
    UUID paymentId,
    String reason
) {}
