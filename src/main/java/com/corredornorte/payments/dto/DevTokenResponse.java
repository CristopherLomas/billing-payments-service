package com.corredornorte.payments.dto;

/**
 * Respuesta con el token JWT de desarrollo generado (Java 21 record).
 */
public record DevTokenResponse(
    String accessToken,
    String tokenType,
    String userId,
    String role,
    Long expiresInSeconds
) {}
