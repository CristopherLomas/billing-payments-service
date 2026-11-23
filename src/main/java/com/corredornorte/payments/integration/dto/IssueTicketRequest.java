package com.corredornorte.payments.integration.dto;

/**
 * Payload para solicitar la emisión del boleto en el Módulo 4 (Diego Chiguano - Booking, Tickets & QR).
 * Contrato real: POST /api/tickets
 */
public record IssueTicketRequest(
    String bookingId
) {}
