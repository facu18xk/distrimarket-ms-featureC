package com.distrimarket.ms.featurec.exception;

/**
 * Inventario rechazó la llamada por permisos (401/403) con el token del usuario que reenvía ventas:
 * no es una caída del servicio, sino que ese rol no puede hacer la operación en inventario.
 */
public class InventarioAccesoDenegadoException extends RuntimeException {

    public InventarioAccesoDenegadoException(String message) {
        super(message);
    }
}
