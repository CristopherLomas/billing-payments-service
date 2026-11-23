package com.corredornorte.payments.controller;

import com.corredornorte.payments.dto.ApprovePaymentRequest;
import com.corredornorte.payments.dto.PaymentAuditLogResponse;
import com.corredornorte.payments.dto.PaymentResponse;
import com.corredornorte.payments.dto.RejectPaymentRequest;
import com.corredornorte.payments.entity.PaymentMethod;
import com.corredornorte.payments.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@Tag(name = "Pagos - Administración", description = "Bandeja de verificación y gestión de comprobantes para Administradores")
public class AdminPaymentController {

    private final PaymentService paymentService;

    @GetMapping("/pending")
    @Operation(summary = "Bandeja de pagos pendientes", description = "Lista pagos en estado PENDING_VERIFICATION y PENDING_CASH ordenados por FIFO (más antiguos primero).")
    @ApiResponse(responseCode = "200", description = "Listado de pagos pendientes")
    public ResponseEntity<Page<PaymentResponse>> getPendingPayments(
            @Parameter(description = "Filtro opcional por método de pago (BANK_TRANSFER, DEUNA, CASH)")
            @RequestParam(required = false) PaymentMethod method,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<PaymentResponse> pendingPayments = paymentService.getPendingPayments(method, pageable);
        return ResponseEntity.ok(pendingPayments);
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Aprobar pago", description = "Aprueba el comprobante de pago, actualiza el estado a PAID y activa la emisión del boleto QR.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago aprobado exitosamente",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pago no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Transición de estado inválida (pago ya finalizado)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<PaymentResponse> approvePayment(
            @Parameter(description = "ID del pago a aprobar") @PathVariable UUID id,
            @RequestBody(required = false) ApprovePaymentRequest request,
            Authentication authentication) {

        String adminId = resolveAdminId(authentication);
        String notes = request != null ? request.notes() : null;

        PaymentResponse response = paymentService.approvePayment(id, adminId, notes);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Rechazar pago", description = "Rechaza el comprobante con motivo obligatorio, actualiza el estado a REJECTED y notifica la liberación del asiento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago rechazado exitosamente",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Motivo de rechazo faltante o inválido",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Pago no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Transición de estado inválida (pago ya finalizado)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<PaymentResponse> rejectPayment(
            @Parameter(description = "ID del pago a rechazar") @PathVariable UUID id,
            @Valid @RequestBody RejectPaymentRequest request,
            Authentication authentication) {

        String adminId = resolveAdminId(authentication);
        PaymentResponse response = paymentService.rejectPayment(id, adminId, request.reason());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/audit")
    @Operation(summary = "Historial de auditoría de un pago", description = "Obtiene la lista cronológica completa de cambios de estado para el pago especificado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial de auditoría obtenido"),
            @ApiResponse(responseCode = "404", description = "Pago no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<List<PaymentAuditLogResponse>> getAuditTrail(
            @Parameter(description = "ID del pago a consultar auditoría") @PathVariable UUID id) {

        List<PaymentAuditLogResponse> auditTrail = paymentService.getAuditTrail(id);
        return ResponseEntity.ok(auditTrail);
    }

    private String resolveAdminId(Authentication authentication) {
        if (authentication != null && authentication.getName() != null
                && !authentication.getName().isBlank()
                && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return "admin-system";
    }
}
