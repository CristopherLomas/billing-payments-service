package com.corredornorte.payments.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Respuesta del Módulo 4 (C++ Crow) tras emitir un boleto QR.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IssueTicketResponse(
    boolean success,
    TicketData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TicketData(
        String ticketId,
        String bookingId,
        String passengerId,
        String tripId,
        Integer seatNumber,
        String issueDate,
        String status,
        String qrToken,
        String createdAt
    ) {}
}
