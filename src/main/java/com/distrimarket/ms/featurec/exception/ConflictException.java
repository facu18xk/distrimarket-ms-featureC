package com.distrimarket.ms.featurec.exception;

/**
 * Se pidió una operación válida en si misma pero imposible en el estado
 * actual del recurso (p.ej. anular una factura ya anulada).
 * El contrato la mapea a 409 Conflict.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
