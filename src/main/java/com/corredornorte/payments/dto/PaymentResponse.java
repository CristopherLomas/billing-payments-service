package com.corredornorte.payments.dto;

import com.corredornorte.payments.entity.Payment;
import com.corredornorte.payments.entity.PaymentMethod;
import com.corredornorte.payments.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Representación inmutable de respuesta de un Pago (Java 21 record).
 */
public record PaymentResponse(
    UUID id,
    String reservationId,
    String tripId,
    String userId,
    BigDecimal amount,
    PaymentMethod paymentMethod,
    String referenceNumber,
    Integer seatNumber,
    PaymentStatus status,
    String reviewedBy,
    OffsetDateTime reviewedAt,
    String rejectionReason,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    /**
     * Constructor de compatibilidad hacia atrás (sin seatNumber).
     */
    public PaymentResponse(
        UUID id,
        String reservationId,
        String tripId,
        String userId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String referenceNumber,
        PaymentStatus status,
        String reviewedBy,
        OffsetDateTime reviewedAt,
        String rejectionReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
        this(id, reservationId, tripId, userId, amount, paymentMethod, referenceNumber, null, status, reviewedBy, reviewedAt, rejectionReason, createdAt, updatedAt);
    }

    public static PaymentResponse fromEntity(Payment payment) {
        if (payment == null) {
            return null;
        }
        return new PaymentResponse(
            payment.getId(),
            payment.getReservationId(),
            payment.getTripId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getPaymentMethod(),
            payment.getReferenceNumber(),
            payment.getSeatNumber(),
            payment.getStatus(),
            payment.getReviewedBy(),
            payment.getReviewedAt(),
            payment.getRejectionReason(),
            payment.getCreatedAt(),
            payment.getUpdatedAt()
        );
    }
}
