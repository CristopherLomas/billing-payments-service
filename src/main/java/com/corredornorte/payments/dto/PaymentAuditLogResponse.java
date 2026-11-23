package com.corredornorte.payments.dto;

import com.corredornorte.payments.entity.PaymentAuditLog;
import com.corredornorte.payments.entity.PaymentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Representación inmutable de la traza de auditoría (Java 21 record).
 */
public record PaymentAuditLogResponse(
    UUID id,
    UUID paymentId,
    PaymentStatus previousStatus,
    PaymentStatus newStatus,
    String changedBy,
    String notes,
    OffsetDateTime changedAt
) {
    public static PaymentAuditLogResponse fromEntity(PaymentAuditLog auditLog) {
        if (auditLog == null) {
            return null;
        }
        return new PaymentAuditLogResponse(
            auditLog.getId(),
            auditLog.getPaymentId(),
            auditLog.getPreviousStatus(),
            auditLog.getNewStatus(),
            auditLog.getChangedBy(),
            auditLog.getNotes(),
            auditLog.getChangedAt()
        );
    }
}
