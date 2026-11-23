package com.corredornorte.payments.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Configuración de seguridad Spring Security:
 * - Sesión Stateless con JWT.
 * - Autorización basada en roles (ROLE_ADMIN, ROLE_PASAJERO).
 * - CORS configurable para consumo desde aplicaciones frontend/móvil.
 * - Respuestas de error de seguridad bajo estándar RFC 7807 (ProblemDetail).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Value("${security.jwt.dev-mode:true}")
    private boolean devMode;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // Rutas públicas de infraestructura y documentación
                    auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();

                    // Endpoints y recursos de prueba en modo desarrollo
                    if (devMode) {
                        auth.requestMatchers("/api/v1/dev/token", "/demo.html").permitAll();
                    }

                    // Rutas administrativas protegidas para ADMIN o STAFF
                    auth.requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "STAFF")
                        // Rutas de pasajeros accesibles para usuarios autenticados (PASAJERO, PASSENGER, STAFF o ADMIN)
                        .requestMatchers("/api/v1/payments/**").hasAnyRole("PASAJERO", "PASSENGER", "STAFF", "ADMIN")
                        .anyRequest().authenticated();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

                            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                                    HttpStatus.UNAUTHORIZED,
                                    "Autenticación requerida. Proporcione un encabezado 'Authorization: Bearer <token>' válido."
                            );
                            problem.setTitle("No autenticado");
                            problem.setType(URI.create("urn:problem-type:unauthorized"));
                            problem.setProperty("timestamp", OffsetDateTime.now(ZoneOffset.UTC));

                            response.getWriter().write(objectMapper.writeValueAsString(problem));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

                            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                                    HttpStatus.FORBIDDEN,
                                    "Acceso denegado: No posee los permisos requeridos (rol requerido) para este recurso."
                            );
                            problem.setTitle("Acceso denegado");
                            problem.setType(URI.create("urn:problem-type:forbidden-access"));
                            problem.setProperty("timestamp", OffsetDateTime.now(ZoneOffset.UTC));

                            response.getWriter().write(objectMapper.writeValueAsString(problem));
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Location", "Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
