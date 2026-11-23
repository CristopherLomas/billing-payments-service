package com.corredornorte.payments.security;

import com.corredornorte.payments.config.JwtService;
import com.corredornorte.payments.dto.ApprovePaymentRequest;
import com.corredornorte.payments.dto.CreatePaymentRequest;
import com.corredornorte.payments.dto.PaymentResponse;
import com.corredornorte.payments.entity.PaymentMethod;
import com.corredornorte.payments.entity.PaymentStatus;
import com.corredornorte.payments.service.PaymentService;
import com.corredornorte.payments.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityRoleAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private ReportService reportService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("Usuario con token PASAJERO puede registrar pagos pero recibe 403 en endpoints admin")
    void passengerCanCreatePaymentButForbiddenOnAdmin() throws Exception {
        String passengerToken = jwtService.generateToken("passenger-001", "PASAJERO");

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        PaymentResponse response = new PaymentResponse(
                UUID.randomUUID(),
                "RES-500",
                "TRIP-1",
                "passenger-001",
                new BigDecimal("15.00"),
                PaymentMethod.DEUNA,
                "REF-PASS-1",
                PaymentStatus.PENDING_VERIFICATION,
                null,
                null,
                null,
                now,
                now
        );

        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq("passenger-001")))
                .thenReturn(response);

        CreatePaymentRequest request = new CreatePaymentRequest(
                "RES-500",
                "TRIP-1",
                new BigDecimal("15.00"),
                PaymentMethod.DEUNA,
                "REF-PASS-1"
        );

        // 1. Debe permitir registrar pago
        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + passengerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("passenger-001"));

        // 2. Debe bloquear acceso a ruta admin con 403 Forbidden
        mockMvc.perform(get("/api/v1/admin/payments/pending")
                        .header("Authorization", "Bearer " + passengerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Usuario con token ADMIN puede acceder a rutas administrativas para aprobar pagos")
    void adminCanApprovePayment() throws Exception {
        String adminToken = jwtService.generateToken("admin-001", "ADMIN");
        UUID paymentId = UUID.randomUUID();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        PaymentResponse approvedResponse = new PaymentResponse(
                paymentId,
                "RES-500",
                "TRIP-1",
                "passenger-001",
                new BigDecimal("15.00"),
                PaymentMethod.DEUNA,
                "REF-PASS-1",
                PaymentStatus.PAID,
                "admin-001",
                now,
                null,
                now,
                now
        );

        when(paymentService.approvePayment(eq(paymentId), eq("admin-001"), any()))
                .thenReturn(approvedResponse);

        ApprovePaymentRequest request = new ApprovePaymentRequest("Aprobación verificada");

        mockMvc.perform(patch("/api/v1/admin/payments/{id}/approve", paymentId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.reviewedBy").value("admin-001"));
    }
}
