package com.corredornorte.payments.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Implementación MOCK de la integración externa.
 * Se activa por defecto o cuando 'integration.mock: true' para pruebas desacopladas.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "integration.mock", havingValue = "true", matchIfMissing = true)
public class MockExternalBookingGateway implements ExternalBookingGateway {

    @Override
    public void notifyPaymentApproved(String reservationId, String tripId, UUID paymentId, String userId) {
        log.info("[MOCK INTEGRATION] >> NOTIFICACIÓN PAGO APROBADO: "
                + "reservationId={}, tripId={}, paymentId={}, userId={} -> Simulando emisión de boleto y QR.",
                reservationId, tripId, paymentId, userId);
    }

    @Override
    public void notifyPaymentRejected(String reservationId, String tripId, UUID paymentId, String userId, Integer seatNumber, String reason) {
        log.warn("[MOCK INTEGRATION] >> NOTIFICACIÓN PAGO RECHAZADO: "
                + "reservationId={}, tripId={}, paymentId={}, userId={}, seatNumber={}, motivo='{}' -> Simulando liberación de asiento en Trips Service (Grupo 3 - Johan Almeida).",
                reservationId, tripId, paymentId, userId, seatNumber, reason);
    }

    @Override
    public void notifyPaymentRejected(String reservationId, String tripId, UUID paymentId, String reason) {
        notifyPaymentRejected(reservationId, tripId, paymentId, null, null, reason);
    }
}
