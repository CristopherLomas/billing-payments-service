package com.corredornorte.payments.repository;

import com.corredornorte.payments.dto.report.DailyReconciliationDto;
import com.corredornorte.payments.dto.report.RevenueReportDto;
import com.corredornorte.payments.entity.Payment;
import com.corredornorte.payments.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Valida si un comprobante / número de referencia ya fue registrado.
     */
    boolean existsByReferenceNumber(String referenceNumber);

    /**
     * Búsqueda por número de comprobante de transferencia o Deuna.
     */
    Optional<Payment> findByReferenceNumber(String referenceNumber);

    /**
     * Búsqueda de pago asociado a una reserva.
     */
    Optional<Payment> findByReservationId(String reservationId);

    /**
     * Historial de pagos por usuario con paginación.
     */
    Page<Payment> findByUserId(String userId, Pageable pageable);

    /**
     * Bandeja de administración para filtrar pagos por estados específicos.
     */
    Page<Payment> findByStatusIn(Collection<PaymentStatus> statuses, Pageable pageable);

    /**
     * Bandeja de administración para filtrar pagos por estados específicos y método de pago.
     */
    Page<Payment> findByStatusInAndPaymentMethod(Collection<PaymentStatus> statuses, com.corredornorte.payments.entity.PaymentMethod paymentMethod, Pageable pageable);

    /**
     * Reporte agregado de recaudación por rango de fechas agrupado por método y estado.
     */
    @Query("""
        SELECT new com.corredornorte.payments.dto.report.RevenueReportDto(
            p.paymentMethod,
            p.status,
            COUNT(p),
            SUM(p.amount)
        )
        FROM Payment p
        WHERE p.createdAt >= :startDate AND p.createdAt <= :endDate
        GROUP BY p.paymentMethod, p.status
        ORDER BY p.paymentMethod ASC, p.status ASC
    """)
    List<RevenueReportDto> getRevenueReportByDateRange(
        @Param("startDate") OffsetDateTime startDate,
        @Param("endDate") OffsetDateTime endDate
    );

    /**
     * Reporte agregado para conciliación de pagos exitosos (PAID) por rango de fechas.
     */
    @Query("""
        SELECT new com.corredornorte.payments.dto.report.DailyReconciliationDto(
            p.paymentMethod,
            COUNT(p),
            SUM(p.amount)
        )
        FROM Payment p
        WHERE p.status = com.corredornorte.payments.entity.PaymentStatus.PAID
          AND p.createdAt >= :startDate
          AND p.createdAt <= :endDate
        GROUP BY p.paymentMethod
        ORDER BY p.paymentMethod ASC
    """)
    List<DailyReconciliationDto> getReconciliationReport(
        @Param("startDate") OffsetDateTime startDate,
        @Param("endDate") OffsetDateTime endDate
    );

    /**
     * Listado cronológico de pagos para conciliación detallada por rango de fechas.
     */
    List<Payment> findByCreatedAtBetweenOrderByCreatedAtAsc(OffsetDateTime startDate, OffsetDateTime endDate);
}
