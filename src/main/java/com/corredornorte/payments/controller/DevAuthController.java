package com.corredornorte.payments.controller;

import com.corredornorte.payments.config.JwtService;
import com.corredornorte.payments.dto.DevTokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador de utilería para generar tokens JWT en entornos de desarrollo local.
 * Desactivable en producción configurando 'security.jwt.dev-mode: false'.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "security.jwt.dev-mode", havingValue = "true")
@Tag(name = "Dev Auth - Generación de Tokens", description = "Generador de tokens JWT para pruebas locales en Swagger y Postman")
public class DevAuthController {

    private final JwtService jwtService;

    @PostMapping("/token")
    @Operation(summary = "Generar token JWT para pruebas", description = "Genera un token JWT firmado válido con el userId y rol especificado (PASAJERO o ADMIN).")
    @ApiResponse(responseCode = "200", description = "Token generado exitosamente")
    public ResponseEntity<DevTokenResponse> generateDevToken(
            @Parameter(description = "Identificador de usuario (sub)", example = "user-123")
            @RequestParam(defaultValue = "user-1") String userId,
            @Parameter(description = "Rol del usuario (PASAJERO o ADMIN)", example = "ADMIN")
            @RequestParam(defaultValue = "PASAJERO") String role) {

        String token = jwtService.generateToken(userId, role);
        log.info("Token JWT de desarrollo generado para userId={}, role={}", userId, role);

        return ResponseEntity.ok(new DevTokenResponse(
                token,
                "Bearer",
                userId,
                role.toUpperCase(),
                86400L
        ));
    }
}
