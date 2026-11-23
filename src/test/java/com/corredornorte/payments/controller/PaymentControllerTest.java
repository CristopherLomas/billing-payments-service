package com.corredornorte.payments.controller;

import com.corredornorte.payments.dto.ApprovePaymentRequest;
import com.corredornorte.payments.dto.CreatePaymentRequest;
import com.corredornorte.payments.dto.PaymentResponse;
import com.corredornorte.payments.dto.ReconciliationResponse;
import com.corredornorte.payments.dto.RejectPaymentRequest;
import com.corredornorte.payments.dto.RevenueReportResponse;
import com.corredornorte.payments.entity.PaymentMethod;
import com.corredornorte.payments.entity.PaymentStatus;
import com.corredornorte.payments.exception.GlobalExceptionHandler;
import com.corredornorte.payments.service.PaymentService;
import com.corredornorte.payments.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private PaymentService paymentService;

    @Mock
    private ReportService reportService;

    @InjectMocks
    private PaymentController paymentController;

    @InjectMocks
    private AdminPaymentController adminPaymentController;

    @InjectMocks
    private ReportController reportController;

    private PaymentResponse samplePayment;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(paymentController, adminPaymentController, reportController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        samplePayment = new PaymentResponse(
                UUID.randomUUID(),
                "RES-100",
                "TRIP-200",
                "passenger-user",
                new BigDecimal("10.00"),
                PaymentMethod.DEUNA,
                "REF-999",
                PaymentStatus.PENDING_VERIFICATION,
                null,
                null,
                null,
                now,
                now
        );
    }

    @Test
    @DisplayName("POST /api/v1/payments -> 201 Created con Location header")
    void createPaymentReturns201() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest(
                "RES-100",
                "TRIP-200",
                new BigDecimal("10.00"),
                PaymentMethod.DEUNA,
                "REF-999"
        );

        when(paymentService.createPayment(any(CreatePaymentRequest.class), anyString()))
                .thenReturn(samplePayment);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(samplePayment.id().toString()))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
    }

    @Test
    @DisplayName("POST /api/v1/payments -> 400 Bad Request cuando el monto es menor a 0.01")
    void createPaymentInvalidAmountReturns400() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest(
                "RES-100",
                "TRIP-200",
                new BigDecimal("0.00"),
                PaymentMethod.DEUNA,
                "REF-999"
        );

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Error de validación"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} -> 200 OK")
    void getPaymentByIdReturns200() throws Exception {
        when(paymentService.getPaymentById(eq(samplePayment.id()), anyString(), anyBoolean()))
                .thenReturn(samplePayment);

        mockMvc.perform(get("/api/v1/payments/{id}", samplePayment.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(samplePayment.id().toString()));
    }

    @Test
    @DisplayName("GET /api/v1/payments/my-payments -> 200 OK paginado")
    void getMyPaymentsReturns200() throws Exception {
        when(paymentService.getMyPayments(anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(samplePayment), org.springframework.data.domain.PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/payments/my-payments?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(samplePayment.id().toString()));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/payments/{id}/approve -> 200 OK")
    void approvePaymentReturns200() throws Exception {
        PaymentResponse approved = new PaymentResponse(
                samplePayment.id(),
                samplePayment.reservationId(),
                samplePayment.tripId(),
                samplePayment.userId(),
                samplePayment.amount(),
                samplePayment.paymentMethod(),
                samplePayment.referenceNumber(),
                PaymentStatus.PAID,
                "admin-system",
                OffsetDateTime.now(ZoneOffset.UTC),
                null,
                samplePayment.createdAt(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        when(paymentService.approvePayment(eq(samplePayment.id()), anyString(), any()))
                .thenReturn(approved);

        ApprovePaymentRequest request = new ApprovePaymentRequest("Aprobado conforme");

        mockMvc.perform(patch("/api/v1/admin/payments/{id}/approve", samplePayment.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.reviewedBy").value("admin-system"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/payments/{id}/reject -> 400 Bad Request si no envía motivo")
    void rejectPaymentWithoutReasonReturns400() throws Exception {
        RejectPaymentRequest request = new RejectPaymentRequest("   ");

        mockMvc.perform(patch("/api/v1/admin/payments/{id}/reject", samplePayment.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Error de validación"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/payments/reports/revenue -> 200 OK")
    void getRevenueReportReturns200() throws Exception {
        OffsetDateTime from = OffsetDateTime.parse("2026-11-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-11-30T23:59:59Z");

        RevenueReportResponse response = new RevenueReportResponse(
                from,
                to,
                new BigDecimal("500.00"),
                new BigDecimal("50.00"),
                new BigDecimal("20.00"),
                35L,
                List.of()
        );

        when(reportService.getRevenueReport(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/payments/reports/revenue")
                        .param("from", "2026-11-01T00:00:00Z")
                        .param("to", "2026-11-30T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaid").value(500.00))
                .andExpect(jsonPath("$.totalTransactions").value(35));
    }

    @Test
    @DisplayName("GET /api/v1/admin/payments/reconciliation -> 200 OK")
    void getReconciliationReportReturns200() throws Exception {
        LocalDate date = LocalDate.parse("2026-11-17");

        ReconciliationResponse response = new ReconciliationResponse(
                date,
                List.of(samplePayment),
                1L,
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of()
        );

        when(reportService.getReconciliationReport(eq(date)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/payments/reconciliation")
                        .param("date", "2026-11-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(1))
                .andExpect(jsonPath("$.totalPaidAmount").value(10.00));
    }
}
