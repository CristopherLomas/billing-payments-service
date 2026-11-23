package com.corredornorte.payments.exception;

public class DuplicateReferenceException extends RuntimeException {

    public DuplicateReferenceException(String referenceNumber) {
        super(String.format("El comprobante con número de referencia [%s] ya existe en el sistema.", referenceNumber));
    }
}
