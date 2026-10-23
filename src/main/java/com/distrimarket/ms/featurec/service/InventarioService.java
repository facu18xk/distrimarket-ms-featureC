package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.dto.DepositoResponseDTO;
import com.distrimarket.commons.dto.EmpleadoResponseDTO;
import com.distrimarket.commons.dto.OrigenMovimientoStock;
import com.distrimarket.commons.dto.ProductoResponseDTO;
import com.distrimarket.commons.dto.StockMovimientoDetalleRequestDTO;
import com.distrimarket.commons.dto.StockMovimientoRequestDTO;
import com.distrimarket.ms.featurec.client.InventarioClient;
import com.distrimarket.ms.featurec.exception.BusinessRuleException;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Servicio de apoyo (sin controlador): único punto de ventas que conversa con inventario.
 * Solo se usa para validar una venta (producto activo, precio, empleado, depósito) y para
 * mover stock. Mostrar una factura no consulta a inventario: la factura lleva solo los ids.
 */
@Slf4j
@Service
public class InventarioService {

    private final InventarioClient inventarioClient;

    public InventarioService(InventarioClient inventarioClient) {
        this.inventarioClient = inventarioClient;
    }

    /** Para validar una operación: si inventario no lo tiene, la operación no puede seguir (404). */
    public ProductoResponseDTO obtenerProducto(Long id) {
        return obtener("Producto", id, inventarioClient::obtenerProducto);
    }

    public DepositoResponseDTO obtenerDeposito(Long id) {
        return obtener("Depósito", id, inventarioClient::obtenerDeposito);
    }

    public EmpleadoResponseDTO obtenerEmpleado(Long id) {
        return obtener("Empleado", id, inventarioClient::obtenerEmpleado);
    }

    /** Para mostrar datos: vacío si inventario ya no lo ofrece (por ejemplo, fue dado de baja). */
    public Optional<ProductoResponseDTO> buscarProducto(Long id) {
        return buscar("Producto", id, inventarioClient::obtenerProducto);
    }

    public Optional<DepositoResponseDTO> buscarDeposito(Long id) {
        return buscar("Depósito", id, inventarioClient::obtenerDeposito);
    }

    /**
     * Mueve existencias en un depósito. Por producto: cantidad negativa descuenta, positiva repone.
     * Inventario lo aplica completo o no aplica nada.
     */
    public void moverStock(Long idDeposito, Map<Long, Integer> cantidadPorProducto, String motivo) {
        List<StockMovimientoDetalleRequestDTO> detalles = new ArrayList<>();
        cantidadPorProducto.forEach((idProducto, cantidad) -> {
            if (cantidad != 0) {
                StockMovimientoDetalleRequestDTO detalle = new StockMovimientoDetalleRequestDTO();
                detalle.setIdProducto(idProducto);
                detalle.setCantidad(cantidad);
                detalles.add(detalle);
            }
        });
        if (detalles.isEmpty()) {
            return;
        }
        StockMovimientoRequestDTO movimiento = new StockMovimientoRequestDTO();
        movimiento.setIdDeposito(idDeposito);
        // Inventario solo acepta movimientos de VENTA de un vendedor (o admin).
        movimiento.setOrigen(OrigenMovimientoStock.VENTA);
        movimiento.setMotivo(motivo);
        movimiento.setDetalles(detalles);

        log.debug("Solicitando a inventario movimiento de stock en depósito {}: {} ({})",
                idDeposito, cantidadPorProducto, motivo);
        try {
            inventarioClient.aplicarMovimientos(movimiento);
        } catch (ResourceNotFoundException ex) {
            // El recurso pedido a ventas (la factura) sí existe: lo que falta es un producto o depósito
            // que inventario dio de baja, así que es una regla de negocio y no un 404.
            log.warn("Inventario no pudo mover el stock del depósito {} ({}): {}", idDeposito, motivo, ex.getMessage());
            throw new BusinessRuleException(
                    "Inventario no puede mover el stock porque un producto o el depósito ya no está disponible: "
                            + ex.getMessage());
        }
        log.info("Inventario aplicó el movimiento de stock en depósito {} ({})", idDeposito, motivo);
    }

    private <T> T obtener(String recurso, Long id, Function<Long, T> consulta) {
        log.debug("Consultando {} {} en inventario", recurso, id);
        try {
            return consulta.apply(id);
        } catch (ResourceNotFoundException ex) {
            // Inventario responde un 404 genérico; ventas informa qué recurso faltó.
            throw new ResourceNotFoundException(recurso, id);
        }
    }

    private <T> Optional<T> buscar(String recurso, Long id, Function<Long, T> consulta) {
        log.debug("Consultando {} {} en inventario", recurso, id);
        try {
            return Optional.ofNullable(consulta.apply(id));
        } catch (ResourceNotFoundException ex) {
            log.debug("{} {} no está disponible en inventario", recurso, id);
            return Optional.empty();
        }
    }
}
