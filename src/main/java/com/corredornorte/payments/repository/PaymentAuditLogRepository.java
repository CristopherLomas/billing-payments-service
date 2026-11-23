package com.corredornorte.payments.repository;

import com.corredornorte.payments.entity.PaymentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentAuditLogRepository extends JpaRepository<PaymentAuditLog, UUID> {

    /**
     * Obtiene la traza cronológica de auditoría de cambios de estado para un pago.
     */
    List<PaymentAuditLog> findByPaymentIdOrderByChangedAtAsc(UUID paymentId);
}
