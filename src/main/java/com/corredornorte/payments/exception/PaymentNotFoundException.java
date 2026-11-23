package com.corredornorte.payments.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID id) {
        super(String.format("Pago con identificador [%s] no encontrado.", id));
    }

    public PaymentNotFoundException(String message) {
        super(message);
    }
}
