package com.corredornorte.payments.integration;

import java.util.UUID;

/**
 * Contrato desacoplado para la comunicación con microservicios externos:
 * - Emisión de boletos QR tras confirmación de pago (Grupo 4 - Diego Chiguano).
 * - Liberación automática de asientos tras rechazo de comprobante (Grupo 3 - Johan Almeida).
 */
public interface ExternalBookingGateway {

    /**
     * Notifica la aprobación del pago para activar la generación y emisión del boleto con código QR.
     *
     * @param reservationId Identificador de la reserva
     * @param tripId        Identificador del turno / viaje
     * @param paymentId     Identificador único del pago aprobado
     * @param userId        Identificador del usuario pasajero
     */
    void notifyPaymentApproved(String reservationId, String tripId, UUID paymentId, String userId);

    /**
     * Notifica el rechazo del pago para liberar inmediatamente el asiento reservado en el microservicio de Trips (Grupo 3).
     *
     * @param reservationId Identificador de la reserva
     * @param tripId        Identificador del turno / viaje (UUID)
     * @param paymentId     Identificador único del pago rechazado
     * @param userId        Identificador del pasajero titular de la retención
     * @param seatNumber    Número de asiento retenido (1 a 40)
     * @param reason        Motivo del rechazo del comprobante
     */
    void notifyPaymentRejected(String reservationId, String tripId, UUID paymentId, String userId, Integer seatNumber, String reason);

    /**
     * Método de compatibilidad sin userId y seatNumber.
     */
    default void notifyPaymentRejected(String reservationId, String tripId, UUID paymentId, String reason) {
        notifyPaymentRejected(reservationId, tripId, paymentId, null, null, reason);
    }
}
