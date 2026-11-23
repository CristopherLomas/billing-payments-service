package com.corredornorte.payments.service;

import com.corredornorte.payments.dto.ReconciliationResponse;
import com.corredornorte.payments.dto.RevenueReportResponse;
import com.corredornorte.payments.dto.report.RevenueReportDto;
import com.corredornorte.payments.entity.Payment;
import com.corredornorte.payments.entity.PaymentMethod;
import com.corredornorte.payments.entity.PaymentStatus;
import com.corredornorte.payments.exception.InvalidPaymentRequestException;
import com.corredornorte.payments.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private ReportService reportService;

    @Nested
    @DisplayName("Reporte de Recaudación (Revenue Report)")
    class RevenueReportTests {

        @Test
        @DisplayName("Debe calcular exactamente los totales en BigDecimal con escala 2 para ingresos, pendientes y rechazados")
        void calculateExactBigDecimalTotalsForRevenue() {
            OffsetDateTime from = OffsetDateTime.of(2026, 11, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime to = OffsetDateTime.of(2026, 11, 30, 23, 59, 59, 0, ZoneOffset.UTC);

            List<RevenueReportDto> mockAggregations = List.of(
                    new RevenueReportDto(PaymentMethod.BANK_TRANSFER, PaymentStatus.PAID, 5L, new BigDecimal("125.50")),
                    new RevenueReportDto(PaymentMethod.DEUNA, PaymentStatus.PAID, 3L, new BigDecimal("45.25")),
                    new RevenueReportDto(PaymentMethod.CASH, PaymentStatus.PENDING_CASH, 4L, new BigDecimal("20.00")),
                    new RevenueReportDto(PaymentMethod.BANK_TRANSFER, PaymentStatus.PENDING_VERIFICATION, 2L, new BigDecimal("30.75")),
                    new RevenueReportDto(PaymentMethod.DEUNA, PaymentStatus.REJECTED, 1L, new BigDecimal("15.00"))
            );

            when(paymentRepository.getRevenueReportByDateRange(from, to)).thenReturn(mockAggregations);

            RevenueReportResponse response = reportService.getRevenueReport(from, to);

            assertThat(response).isNotNull();
            assertThat(response.from()).isEqualTo(from);
            assertThat(response.to()).isEqualTo(to);

            // Validaciones matemáticas exactas en BigDecimal
            // totalPaid = 125.50 + 45.25 = 170.75
            assertThat(response.totalPaid()).isEqualByComparingTo(new BigDecimal("170.75"));
            // totalPending = 20.00 + 30.75 = 50.75
            assertThat(response.totalPending()).isEqualByComparingTo(new BigDecimal("50.75"));
            // totalRejected = 15.00
            assertThat(response.totalRejected()).isEqualByComparingTo(new BigDecimal("15.00"));
            // Total transacciones = 5 + 3 + 4 + 2 + 1 = 15
            assertThat(response.totalTransactions()).isEqualTo(15L);

            // Desglose por método de pago
            assertThat(response.breakdownByMethod()).hasSize(3);

            RevenueReportResponse.MethodRevenueSummary bankTransferSummary = response.breakdownByMethod().stream()
                    .filter(m -> m.paymentMethod() == PaymentMethod.BANK_TRANSFER)
                    .findFirst()
                    .orElseThrow();
            assertThat(bankTransferSummary.transactionCount()).isEqualTo(7L); // 5 paid + 2 pending
            assertThat(bankTransferSummary.paidAmount()).isEqualByComparingTo(new BigDecimal("125.50"));

            RevenueReportResponse.MethodRevenueSummary deunaSummary = response.breakdownByMethod().stream()
                    .filter(m -> m.paymentMethod() == PaymentMethod.DEUNA)
                    .findFirst()
                    .orElseThrow();
            assertThat(deunaSummary.transactionCount()).isEqualTo(4L); // 3 paid + 1 rejected
            assertThat(deunaSummary.paidAmount()).isEqualByComparingTo(new BigDecimal("45.25"));

            RevenueReportResponse.MethodRevenueSummary cashSummary = response.breakdownByMethod().stream()
                    .filter(m -> m.paymentMethod() == PaymentMethod.CASH)
                    .findFirst()
                    .orElseThrow();
            assertThat(cashSummary.transactionCount()).isEqualTo(4L);
            assertThat(cashSummary.paidAmount()).isEqualByComparingTo(new BigDecimal("0.00"));
        }

        @Test
        @DisplayName("Debe manejar lista vacía devolviendo ceros con escala 2")
        void handleEmptyAggregations() {
            OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
            OffsetDateTime to = OffsetDateTime.now(ZoneOffset.UTC);

            when(paymentRepository.getRevenueReportByDateRange(from, to)).thenReturn(List.of());

            RevenueReportResponse response = reportService.getRevenueReport(from, to);

            assertThat(response.totalPaid()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.totalPending()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.totalRejected()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.totalTransactions()).isEqualTo(0L);
        }

        @Test
        @DisplayName("Debe lanzar InvalidPaymentRequestException cuando las fechas son nulas o from es posterior a to")
        void throwWhenDatesInvalid() {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            assertThatThrownBy(() -> reportService.getRevenueReport(null, now))
                    .isInstanceOf(InvalidPaymentRequestException.class)
                    .hasMessageContaining("Las fechas de inicio 'from' y fin 'to' son obligatorias");

            assertThatThrownBy(() -> reportService.getRevenueReport(now, null))
                    .isInstanceOf(InvalidPaymentRequestException.class)
                    .hasMessageContaining("Las fechas de inicio 'from' y fin 'to' son obligatorias");

            assertThatThrownBy(() -> reportService.getRevenueReport(now.plusDays(1), now))
                    .isInstanceOf(InvalidPaymentRequestException.class)
                    .hasMessageContaining("debe ser anterior o igual");
        }
    }

    @Nested
    @DisplayName("Reporte de Conciliación Diaria (Reconciliation Report)")
    class ReconciliationReportTests {

        @Test
        @DisplayName("Debe calcular exactamente los totales en BigDecimal de la conciliación diaria")
        void calculateExactDailyReconciliationTotals() {
            LocalDate date = LocalDate.of(2026, 11, 17);
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            Payment p1 = Payment.builder()
                    .id(UUID.randomUUID())
                    .reservationId("RES-1")
                    .tripId("TRIP-1")
                    .userId("USR-1")
                    .amount(new BigDecimal("10.25"))
                    .paymentMethod(PaymentMethod.BANK_TRANSFER)
                    .status(PaymentStatus.PAID)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            Payment p2 = Payment.builder()
                    .id(UUID.randomUUID())
                    .reservationId("RES-2")
                    .tripId("TRIP-1")
                    .userId("USR-2")
                    .amount(new BigDecimal("25.50"))
                    .paymentMethod(PaymentMethod.DEUNA)
                    .status(PaymentStatus.PAID)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            Payment p3 = Payment.builder()
                    .id(UUID.randomUUID())
                    .reservationId("RES-3")
                    .tripId("TRIP-2")
                    .userId("USR-3")
                    .amount(new BigDecimal("5.00"))
                    .paymentMethod(PaymentMethod.CASH)
                    .status(PaymentStatus.PENDING_CASH)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            Payment p4 = Payment.builder()
                    .id(UUID.randomUUID())
                    .reservationId("RES-4")
                    .tripId("TRIP-2")
                    .userId("USR-4")
                    .amount(new BigDecimal("12.00"))
                    .paymentMethod(PaymentMethod.BANK_TRANSFER)
                    .status(PaymentStatus.REJECTED)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            when(paymentRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any()))
                    .thenReturn(List.of(p1, p2, p3, p4));

            ReconciliationResponse response = reportService.getReconciliationReport(date);

            assertThat(response).isNotNull();
            assertThat(response.date()).isEqualTo(date);
            assertThat(response.totalTransactions()).isEqualTo(4L);
            assertThat(response.payments()).hasSize(4);

            // Validaciones BigDecimal
            // totalPaidAmount = 10.25 + 25.50 = 35.75
            assertThat(response.totalPaidAmount()).isEqualByComparingTo(new BigDecimal("35.75"));
            // totalPendingAmount = 5.00
            assertThat(response.totalPendingAmount()).isEqualByComparingTo(new BigDecimal("5.00"));
            // totalRejectedAmount = 12.00
            assertThat(response.totalRejectedAmount()).isEqualByComparingTo(new BigDecimal("12.00"));

            // Desglose por método de pago
            assertThat(response.summaryByMethod()).hasSize(3);

            ReconciliationResponse.MethodReconciliationSummary bankSummary = response.summaryByMethod().stream()
                    .filter(m -> m.paymentMethod() == PaymentMethod.BANK_TRANSFER)
                    .findFirst()
                    .orElseThrow();
            assertThat(bankSummary.transactionCount()).isEqualTo(2L);
            assertThat(bankSummary.totalAmount()).isEqualByComparingTo(new BigDecimal("10.25"));
        }
    }
}
