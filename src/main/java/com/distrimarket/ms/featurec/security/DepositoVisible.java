package com.distrimarket.ms.featurec.security;

import com.distrimarket.commons.security.UsuarioActual;

import java.util.Objects;
import java.util.Optional;

/**
 * Qué facturas puede ver cada usuario en ventas: un ADMIN ve todas; un empleado con depósito asignado
 * (en su token) solo las de su depósito; un empleado sin depósito asignado ve todas, porque elige
 * el depósito en cada operación.
 */
public final class DepositoVisible {

    private DepositoVisible() {
    }

    /** Depósito al que se limita el usuario; vacío = puede ver todos. */
    public static Optional<Long> propio() {
        return UsuarioActual.esAdmin() ? Optional.empty() : UsuarioActual.idDeposito();
    }

    /** Filtro de depósito de un listado: el propio si está limitado; si no, el que pidió. */
    public static Long filtro(Long pedido) {
        return propio().orElse(pedido);
    }

    /** true si el usuario puede ver una factura de ese depósito. */
    public static boolean puedeVer(Long idDepositoFactura) {
        return propio().map(propio -> Objects.equals(propio, idDepositoFactura)).orElse(true);
    }
}
