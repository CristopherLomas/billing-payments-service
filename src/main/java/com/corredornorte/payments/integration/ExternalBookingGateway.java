package com.corredornorte.payments.integration;

import java.util.UUID;

/**
 * Contrato desacoplado para la comunicación con microservicios externos:
 * - Emisión de boletos QR tras confirmación de pago.
 * - Liberación automática de asientos tras rechazo de comprobante.
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
     * Notifica el rechazo del pago para liberar inmediatamente el asiento reservado.
     *
     * @param reservationId Identificador de la reserva
     * @param tripId        Identificador del turno / viaje
     * @param paymentId     Identificador único del pago rechazado
     * @param reason        Motivo del rechazo del comprobante
     */
    void notifyPaymentRejected(String reservationId, String tripId, UUID paymentId, String reason);
}
