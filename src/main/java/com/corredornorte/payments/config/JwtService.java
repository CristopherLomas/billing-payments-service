package com.corredornorte.payments.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de seguridad para validación y extracción de tokens JWT.
 * 
 * Soporta dos modalidades:
 * 1. Firma Asimétrica RSA (RS256): Descarga y valida claves públicas mediante JWKS
 *    desde el microservicio de Autenticación de David Cadena (http://10.10.10.5:5001/.well-known/jwks.json).
 * 2. Firma Simétrica HMAC (HS256/HS512): Modo local y desarrollo rápido (/api/v1/dev/token).
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;
    private final String jwksUri;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, PublicKey> rsaPublicKeysCache = new ConcurrentHashMap<>();

    public JwtService(
            @Value("${security.jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secret,
            @Value("${security.jwt.expiration-ms:86400000}") long expirationMs,
            @Value("${security.jwt.jwks-uri:http://10.10.10.5:5001/.well-known/jwks.json}") String jwksUri) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
        this.jwksUri = jwksUri;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
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

    /**
     * Extrae los claims del token detectando dinámicamente si es RS256 (JWKS) o HMAC.
     */
    private Claims extractClaims(String token) {
        String[] parts = token.split("\\.");
        if (parts.length >= 2) {
            try {
                String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                JsonNode headerNode = objectMapper.readTree(headerJson);
                String alg = headerNode.path("alg").asText("");
                String kid = headerNode.path("kid").asText(null);

                // Si el token fue firmado con RSA (RS256 de David Cadena)
                if (alg.startsWith("RS") || kid != null) {
                    PublicKey rsaPublicKey = getRsaPublicKey(kid);
                    if (rsaPublicKey != null) {
                        return Jwts.parser()
                                .verifyWith(rsaPublicKey)
                                .build()
                                .parseSignedClaims(token)
                                .getPayload();
                    }
                }
            } catch (Exception e) {
                log.debug("Intento de verificación RSA/JWKS no exitoso: {}. Probando clave simétrica...", e.getMessage());
            }
        }

        // Verificación con clave simétrica (modo local / tests)
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Obtiene la clave pública RSA desde la caché o consultando el endpoint JWKS.
     */
    private PublicKey getRsaPublicKey(String kid) {
        if (kid != null && rsaPublicKeysCache.containsKey(kid)) {
            return rsaPublicKeysCache.get(kid);
        }

        refreshJwksKeys();

        if (kid != null) {
            return rsaPublicKeysCache.get(kid);
        }

        // Si no hay kid, retornar la primera clave RSA disponible
        return rsaPublicKeysCache.values().stream().findFirst().orElse(null);
    }

    /**
     * Descarga las claves públicas desde el endpoint JWKS y las almacena en memoria.
     */
    private synchronized void refreshJwksKeys() {
        if (jwksUri == null || jwksUri.isBlank()) {
            return;
        }

        try {
            log.info("Consultando claves públicas JWKS desde: {}", jwksUri);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jwksUri))
                    .timeout(Duration.ofSeconds(4))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode keysNode = root.path("keys");

                if (keysNode.isArray()) {
                    KeyFactory keyFactory = KeyFactory.getInstance("RSA");

                    for (JsonNode keyNode : keysNode) {
                        String keyId = keyNode.path("kid").asText(null);
                        String kty = keyNode.path("kty").asText("");
                        String nStr = keyNode.path("n").asText("");
                        String eStr = keyNode.path("e").asText("");

                        if ("RSA".equalsIgnoreCase(kty) && !nStr.isEmpty() && !eStr.isEmpty()) {
                            byte[] nBytes = Base64.getUrlDecoder().decode(nStr);
                            byte[] eBytes = Base64.getUrlDecoder().decode(eStr);

                            BigInteger modulus = new BigInteger(1, nBytes);
                            BigInteger exponent = new BigInteger(1, eBytes);

                            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                            PublicKey publicKey = keyFactory.generatePublic(spec);

                            if (keyId != null) {
                                rsaPublicKeysCache.put(keyId, publicKey);
                            }
                            // También almacenar con fallback default
                            rsaPublicKeysCache.putIfAbsent("default", publicKey);
                        }
                    }
                    log.info("JWKS cargado exitosamente. Claves registradas: {}", rsaPublicKeysCache.keySet());
                }
            } else {
                log.warn("El endpoint JWKS respondió con código HTTP: {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("No se pudo conectar al endpoint JWKS ({}): {}", jwksUri, e.getMessage());
        }
    }

    private String normalizeRole(String rawRole) {
        String trimmed = rawRole.trim().toUpperCase();
        return trimmed.startsWith("ROLE_") ? trimmed : "ROLE_" + trimmed;
    }
}
