package com.corredornorte.payments.controller;

import com.corredornorte.payments.dto.CreatePaymentRequest;
import com.corredornorte.payments.dto.PaymentResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Pagos - Pasajeros", description = "Endpoints para registro de comprobantes y consulta de pagos por pasajeros")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Registrar nuevo pago", description = "Registra un comprobante de pago bancario, DeUna o selecciona pago en efectivo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pago registrado exitosamente",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida o campos faltantes",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Número de comprobante duplicado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            Authentication authentication) {

        String userId = resolveUserId(authentication);
        PaymentResponse response = paymentService.createPayment(request, userId);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar estado de pago por ID", description = "Permite al pasajero o quiosco consultar el estado actual del pago.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago encontrado",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "403", description = "No autorizado para consultar este pago",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Pago no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<PaymentResponse> getPaymentById(
            @Parameter(description = "Identificador único del pago") @PathVariable UUID id,
            Authentication authentication) {

        String requesterId = resolveUserId(authentication);
        boolean isAdmin = checkIsAdmin(authentication);

        PaymentResponse response = paymentService.getPaymentById(id, requesterId, isAdmin);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-payments")
    @Operation(summary = "Listar mis pagos", description = "Retorna el historial paginado de pagos del pasajero autenticado.")
    @ApiResponse(responseCode = "200", description = "Listado paginado obtenido correctamente")
    public ResponseEntity<Page<PaymentResponse>> getMyPayments(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {

        String userId = resolveUserId(authentication);
        Page<PaymentResponse> response = paymentService.getMyPayments(userId, pageable);
        return ResponseEntity.ok(response);
    }

    private String resolveUserId(Authentication authentication) {
        if (authentication != null && authentication.getName() != null
                && !authentication.getName().isBlank()
                && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return "passenger-user";
    }

    private boolean checkIsAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_ADMIN")
                        || a.getAuthority().equalsIgnoreCase("ADMIN"));
    }
}
