package com.corredornorte.payments.integration;

import com.corredornorte.payments.exception.ExternalIntegrationException;
import com.corredornorte.payments.integration.dto.QrActivationRequest;
import com.corredornorte.payments.integration.dto.SeatReleaseRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.UUID;

/**
 * Implementación HTTP real de la integración externa usando Spring RestClient.
 * Se activa cuando 'integration.mock: false'.
 * Conecta con:
 * - QR Service (Grupo 4 - Diego Chiguano): emisión de boleto QR tras pago.
 * - Trips Service (Grupo 3 - Johan Almeida): liberación de asiento tras rechazo de comprobante.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "integration.mock", havingValue = "false")
public class HttpExternalBookingGateway implements ExternalBookingGateway {

    private final RestClient restClient;
    private final String qrServiceUrl;
    private final String bookingServiceUrl;
    private final String tripsServiceUrl;

    public HttpExternalBookingGateway(
            RestClient restClient,
            @Value("${integration.qr-service-url}") String qrServiceUrl,
            @Value("${integration.booking-service-url}") String bookingServiceUrl,
            @Value("${integration.trips-service-url:http://10.10.10.13:5001}") String tripsServiceUrl) {
        this.restClient = restClient;
        this.qrServiceUrl = qrServiceUrl;
        this.bookingServiceUrl = bookingServiceUrl;
        this.tripsServiceUrl = tripsServiceUrl;
    }

    @Override
    public void notifyPaymentApproved(String reservationId, String tripId, UUID paymentId, String userId) {
        String endpoint = qrServiceUrl + "/api/v1/tickets/qr/activate";
        QrActivationRequest request = new QrActivationRequest(reservationId, tripId, paymentId, userId);

        log.info("[HTTP INTEGRATION] >> Notificando aprobación a QR Service (Grupo 4): URL={}, payload={}", endpoint, request);

        try {
            restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, response) -> {
                        String responseBody = new String(response.getBody().readAllBytes());
                        throw new ExternalIntegrationException("qr-service", response.getStatusCode().value(), responseBody);
                    })
                    .toBodilessEntity();

            log.info("[HTTP INTEGRATION] << Aprobación confirmada por QR Service para reservationId={}", reservationId);
        } catch (ExternalIntegrationException e) {
            log.error("[HTTP INTEGRATION] Error de negocio retornado por QR Service: {}", e.getMessage());
            throw e;
        } catch (ResourceAccessException e) {
            log.error("[HTTP INTEGRATION] Fallo de conectividad o timeout con QR Service en {}: {}", endpoint, e.getMessage());
            throw new ExternalIntegrationException("qr-service", "Timeout o conexión rechazada: " + e.getMessage(), e);
        } catch (RestClientResponseException e) {
            log.error("[HTTP INTEGRATION] Error HTTP desde QR Service: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ExternalIntegrationException("qr-service", e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("[HTTP INTEGRATION] Error inesperado al invocar QR Service: {}", e.getMessage(), e);
            throw new ExternalIntegrationException("qr-service", "Fallo inesperado: " + e.getMessage(), e);
        }
    }

    @Override
    public void notifyPaymentRejected(String reservationId, String tripId, UUID paymentId, String userId, Integer seatNumber, String reason) {
        if (seatNumber != null && tripId != null) {
            // Llamada directa al microservicio de Trips (Grupo 3 - Johan Almeida)
            String endpoint = tripsServiceUrl + "/api/v1/trips/" + tripId + "/seats/" + seatNumber + "/hold";
            Map<String, String> body = Map.of("userId", userId != null ? userId : "unknown");

            log.warn("[HTTP INTEGRATION] >> Liberando asiento en Trips Service (Grupo 3): DELETE URL={}, userId={}", endpoint, body.get("userId"));

            try {
                restClient.method(HttpMethod.DELETE)
                        .uri(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, response) -> {
                            String responseBody = new String(response.getBody().readAllBytes());
                            throw new ExternalIntegrationException("trips-service", response.getStatusCode().value(), responseBody);
                        })
                        .toBodilessEntity();

                log.info("[HTTP INTEGRATION] << Asiento {} liberado exitosamente en Trips Service para tripId={}", seatNumber, tripId);
            } catch (ExternalIntegrationException e) {
                log.error("[HTTP INTEGRATION] Error retornado por Trips Service: {}", e.getMessage());
                throw e;
            } catch (ResourceAccessException e) {
                log.error("[HTTP INTEGRATION] Fallo de conectividad con Trips Service en {}: {}", endpoint, e.getMessage());
                throw new ExternalIntegrationException("trips-service", "Timeout o conexión rechazada: " + e.getMessage(), e);
            } catch (RestClientResponseException e) {
                log.error("[HTTP INTEGRATION] Error HTTP desde Trips Service: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new ExternalIntegrationException("trips-service", e.getStatusCode().value(), e.getResponseBodyAsString());
            } catch (Exception e) {
                log.error("[HTTP INTEGRATION] Error inesperado al invocar Trips Service: {}", e.getMessage(), e);
                throw new ExternalIntegrationException("trips-service", "Fallo inesperado: " + e.getMessage(), e);
            }
        } else {
            // Fallback al endpoint de reserva genérico si no se especificó seatNumber
            String endpoint = bookingServiceUrl + "/api/v1/bookings/release";
            SeatReleaseRequest request = new SeatReleaseRequest(reservationId, tripId, paymentId, reason);

            log.warn("[HTTP INTEGRATION] >> Notificando rechazo a Booking Service (fallback genérico): URL={}, payload={}", endpoint, request);

            try {
                restClient.post()
                        .uri(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, response) -> {
                            String responseBody = new String(response.getBody().readAllBytes());
                            throw new ExternalIntegrationException("booking-service", response.getStatusCode().value(), responseBody);
                        })
                        .toBodilessEntity();

                log.info("[HTTP INTEGRATION] << Asiento liberado exitosamente en Booking Service para reservationId={}", reservationId);
            } catch (ExternalIntegrationException e) {
                log.error("[HTTP INTEGRATION] Error de negocio retornado por Booking Service: {}", e.getMessage());
                throw e;
            } catch (ResourceAccessException e) {
                log.error("[HTTP INTEGRATION] Fallo de conectividad o timeout con Booking Service en {}: {}", endpoint, e.getMessage());
                throw new ExternalIntegrationException("booking-service", "Timeout o conexión rechazada: " + e.getMessage(), e);
            } catch (RestClientResponseException e) {
                log.error("[HTTP INTEGRATION] Error HTTP desde Booking Service: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new ExternalIntegrationException("booking-service", e.getStatusCode().value(), e.getResponseBodyAsString());
            } catch (Exception e) {
                log.error("[HTTP INTEGRATION] Error inesperado al invocar Booking Service: {}", e.getMessage(), e);
                throw new ExternalIntegrationException("booking-service", "Fallo inesperado: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void notifyPaymentRejected(String reservationId, String tripId, UUID paymentId, String reason) {
        notifyPaymentRejected(reservationId, tripId, paymentId, null, null, reason);
    }
}
