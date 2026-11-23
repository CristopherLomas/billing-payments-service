package com.corredornorte.payments.exception;

public class UnauthorizedPaymentAccessException extends RuntimeException {

    public UnauthorizedPaymentAccessException(String message) {
        super(message);
    }
}
