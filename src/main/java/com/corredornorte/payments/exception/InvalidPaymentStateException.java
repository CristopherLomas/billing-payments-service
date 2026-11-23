package com.corredornorte.payments.exception;

import com.corredornorte.payments.entity.PaymentStatus;

import java.util.UUID;

public class InvalidPaymentStateException extends RuntimeException {

    public InvalidPaymentStateException(UUID paymentId, PaymentStatus currentStatus, String message) {
        super(String.format("Operación no permitida para el pago [%s] en estado [%s]: %s", paymentId, currentStatus, message));
    }
}
