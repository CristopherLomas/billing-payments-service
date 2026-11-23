package com.corredornorte.payments.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manejador centralizado de excepciones que devuelve respuestas estructuradas
 * bajo el estándar RFC 7807 (ProblemDetail).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TIMESTAMP_PROPERTY = "timestamp";
    private static final String ERRORS_PROPERTY = "errors";

    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail handlePaymentNotFound(PaymentNotFoundException ex) {
        log.warn("Recurso no encontrado: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Pago no encontrado");
        problem.setType(URI.create("urn:problem-type:payment-not-found"));
        problem.setProperty(TIMESTAMP_PROPERTY, OffsetDateTime.now(ZoneOffset.UTC));
        return problem;
    }

    @ExceptionHandler(DuplicateReferenceException.class)
    public ProblemDetail handleDuplicateReference(DuplicateReferenceException ex) {
        log.warn("Conflicto de comprobante duplicado: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Comprobante duplicado");
        problem.setType(URI.create("urn:problem-type:duplicate-reference"));
        problem.setProperty(TIMESTAMP_PROPERTY, OffsetDateTime.now(ZoneOffset.UTC));
        return problem;
    }

    @ExceptionHandler(InvalidPaymentStateException.class)
    public ProblemDetail handleInvalidPaymentState(InvalidPaymentStateException ex) {
        log.warn("Transición de estado rechazada: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Estado de pago inválido");
        problem.setType(URI.create("urn:problem-type:invalid-payment-state"));
        problem.setProperty(TIMESTAMP_PROPERTY, OffsetDateTime.now(ZoneOffset.UTC));
        return problem;
    }

    @ExceptionHandler(InvalidPaymentRequestException.class)
    public ProblemDetail handleInvalidPaymentRequest(InvalidPaymentRequestException ex) {
        log.warn("Solicitud de pago mal formada: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Solicitud inválida");
        problem.setType(URI.create("urn:problem-type:invalid-payment-request"));
        problem.setProperty(TIMESTAMP_PROPERTY, OffsetDateTime.now(ZoneOffset.UTC));
        return problem;
    }

    @ExceptionHandler(UnauthorizedPaymentAccessException.class)
    public ProblemDetail handleUnauthorizedAccess(UnauthorizedPaymentAccessException ex) {
        log.warn("Intento de acceso no autorizado: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Acceso denegado");
        problem.setType(URI.create("urn:problem-type:forbidden-access"));
        problem.setProperty(TIMESTAMP_PROPERTY, OffsetDateTime.now(ZoneOffset.UTC));
        return problem;
    }

    @ExceptionHandler(ExternalIntegrationException.class)
    public ProblemDetail handleExternalIntegration(ExternalIntegrationException ex) {
        log.error("Fallo de comunicación externa con [{}]: {}", ex.getServiceName(), ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setTitle("Error en integración externa");
        problem.setType(URI.create("urn:problem-type:external-integration-error"));
        problem.setProperty("serviceName", ex.getServiceName());
        if (ex.getStatusCode() != null) {
            problem.setProperty("upstreamStatusCode", ex.getStatusCode());
        }
        problem.setProperty(TIMESTAMP_PROPERTY, OffsetDateTime.now(ZoneOffset.UTC));
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        log.warn("Error de validación en parámetros de entrada: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Uno o más campos presentan errores de validación.");
        problem.setTitle("Error de validación");
        problem.setType(URI.create("urn:problem-type:validation-error"));

        List<Map<String, String>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .toList();

        problem.setProperty(ERRORS_PROPERTY, errors);
        problem.setProperty(TIMESTAMP_PROPERTY, OffsetDateTime.now(ZoneOffset.UTC));
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception ex) {
        log.error("Error inesperado en el servidor: {}", ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ha ocurrido un error interno. Por favor contacte al administrador si el problema persiste."
        );
        problem.setTitle("Error interno del servidor");
        problem.setType(URI.create("urn:problem-type:internal-server-error"));
        problem.setProperty(TIMESTAMP_PROPERTY, OffsetDateTime.now(ZoneOffset.UTC));
        return problem;
    }

    private Map<String, String> formatFieldError(FieldError fieldError) {
        Map<String, String> errorDetail = new HashMap<>();
        errorDetail.put("field", fieldError.getField());
        errorDetail.put("message", fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Valor inválido");
        return errorDetail;
    }
}
