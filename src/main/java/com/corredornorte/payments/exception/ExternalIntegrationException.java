package com.corredornorte.payments.exception;

import lombok.Getter;

/**
 * Excepción controlada para fallos de comunicación de red o respuestas HTTP de error
 * con servicios externos (QR Service, Booking Service).
 */
@Getter
public class ExternalIntegrationException extends RuntimeException {

    private final String serviceName;
    private final Integer statusCode;

    public ExternalIntegrationException(String serviceName, String message) {
        super(String.format("Error de integración con servicio [%s]: %s", serviceName, message));
        this.serviceName = serviceName;
        this.statusCode = null;
    }

    public ExternalIntegrationException(String serviceName, String message, Throwable cause) {
        super(String.format("Error de integración con servicio [%s]: %s", serviceName, message), cause);
        this.serviceName = serviceName;
        this.statusCode = null;
    }

    public ExternalIntegrationException(String serviceName, int statusCode, String message) {
        super(String.format("Error HTTP %d desde [%s]: %s", statusCode, serviceName, message));
        this.serviceName = serviceName;
        this.statusCode = statusCode;
    }
}
