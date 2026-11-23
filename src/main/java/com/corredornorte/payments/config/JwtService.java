package com.corredornorte.payments.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Servicio para validación y generación de tokens JWT.
 * Implementa firma y verificación simétrica HMAC-SHA256.
 *
 * =============================================================================
 * GUÍA DE MIGRACIÓN ARQUITECTURAL HACIA ASIMÉTRICA (RS256):
 * Si el microservicio central de Autenticación utiliza firma asimétrica (clave privada en Auth,
 * clave pública en los microservicios consumidores):
 * 1. Obtener la clave pública RSA (formato PEM o endpoint JWKS: /.well-known/jwks.json).
 * 2. Generar el java.security.PublicKey correspondiente:
 *    KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes))
 * 3. En la verificación, reemplazar 'secretKey' por 'publicKey':
 *    Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token)
 * 4. El resto de la extracción de claims (sub, role, roles) permanece 100% idéntico.
 * =============================================================================
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(
            @Value("${security.jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secret,
            @Value("${security.jwt.expiration-ms:86400000}") long expirationMs) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    /**
     * Genera un token JWT para pruebas locales de desarrollo.
     */
    public String generateToken(String userId, String role) {
        String normalizedRole = role.toUpperCase().replace("ROLE_", "");
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userId)
                .claim("role", normalizedRole)
                .claim("roles", List.of(normalizedRole))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Extrae el identificador de usuario (claim 'sub').
     */
    public String extractUserId(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Extrae y normaliza las autoridades (claims 'role' o 'roles') con prefijo 'ROLE_'.
     */
    public List<GrantedAuthority> extractAuthorities(String token) {
        Claims claims = extractClaims(token);
        List<GrantedAuthority> authorities = new ArrayList<>();

        // 1. Extraer claim singular "role"
        Object roleObj = claims.get("role");
        if (roleObj instanceof String roleStr && !roleStr.isBlank()) {
            authorities.add(new SimpleGrantedAuthority(normalizeRole(roleStr)));
        }

        // 2. Extraer claim plural "roles"
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof Collection<?> rolesColl) {
            for (Object r : rolesColl) {
                if (r instanceof String rStr && !rStr.isBlank()) {
                    String normalized = normalizeRole(rStr);
                    if (authorities.stream().noneMatch(a -> a.getAuthority().equals(normalized))) {
                        authorities.add(new SimpleGrantedAuthority(normalized));
                    }
                }
            }
        } else if (rolesObj instanceof String rolesStr && !rolesStr.isBlank()) {
            String normalized = normalizeRole(rolesStr);
            if (authorities.stream().noneMatch(a -> a.getAuthority().equals(normalized))) {
                authorities.add(new SimpleGrantedAuthority(normalized));
            }
        }

        // Fallback por defecto si no vinieron roles
        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_PASAJERO"));
        }

        return authorities;
    }

    /**
     * Valida la firma criptográfica y la vigencia temporal del token.
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getExpiration() != null && claims.getExpiration().after(new Date());
        } catch (Exception e) {
            log.debug("Token JWT no válido: {}", e.getMessage());
            return false;
        }
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String normalizeRole(String rawRole) {
        String trimmed = rawRole.trim().toUpperCase();
        return trimmed.startsWith("ROLE_") ? trimmed : "ROLE_" + trimmed;
    }
}
