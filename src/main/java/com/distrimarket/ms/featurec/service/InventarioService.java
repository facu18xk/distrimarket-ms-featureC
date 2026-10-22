package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.dto.DepositoResponseDTO;
import com.distrimarket.commons.dto.EmpleadoResponseDTO;
import com.distrimarket.commons.dto.ProductoResponseDTO;
import com.distrimarket.commons.dto.StockMovimientoDetalleRequestDTO;
import com.distrimarket.commons.dto.StockMovimientoRequestDTO;
import com.distrimarket.ms.featurec.config.CacheConfig;
import com.distrimarket.ms.featurec.config.DomainCache;
import com.distrimarket.ms.featurec.client.InventarioClient;
import org.springframework.cache.annotation.Cacheable;
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
 *
 * <p>Caché: las validaciones de una venta (producto activo, precio, stock) siempre consultan a
 * inventario. Solo lo que ventas muestra (nombre del producto, vendedor, depósito de una factura)
 * se lee de caché con {@code mostrar*}; cada consulta fresca de una validación lo actualiza.</p>
 */
@Slf4j
@Service
public class InventarioService {

    private final InventarioClient inventarioClient;
    private final DomainCache domainCache;

    public InventarioService(InventarioClient inventarioClient, DomainCache domainCache) {
        this.inventarioClient = inventarioClient;
        this.domainCache = domainCache;
    }

    /** Para validar una operación: si inventario no lo tiene, la operación no puede seguir (404). */
    public ProductoResponseDTO obtenerProducto(Long id) {
        return refrescar(CacheConfig.INVENTARIO_PRODUCTO, id,
                obtener("Producto", id, inventarioClient::obtenerProducto));
    }

    public DepositoResponseDTO obtenerDeposito(Long id) {
        return refrescar(CacheConfig.INVENTARIO_DEPOSITO, id,
                obtener("Depósito", id, inventarioClient::obtenerDeposito));
    }

    public EmpleadoResponseDTO obtenerEmpleado(Long id) {
        return refrescar(CacheConfig.INVENTARIO_EMPLEADO, id,
                obtener("Empleado", id, inventarioClient::obtenerEmpleado));
    }

    /** Para mostrar en una factura. Lo que inventario ya no ofrece (vacío) no se guarda. */
    @Cacheable(cacheNames = CacheConfig.INVENTARIO_PRODUCTO, key = "#id", unless = "#result == null")
    public Optional<ProductoResponseDTO> mostrarProducto(Long id) {
        return buscarProducto(id);
    }

    @Cacheable(cacheNames = CacheConfig.INVENTARIO_DEPOSITO, key = "#id", unless = "#result == null")
    public Optional<DepositoResponseDTO> mostrarDeposito(Long id) {
        return buscarDeposito(id);
    }

    @Cacheable(cacheNames = CacheConfig.INVENTARIO_EMPLEADO, key = "#id", unless = "#result == null")
    public Optional<EmpleadoResponseDTO> mostrarEmpleado(Long id) {
        return buscarEmpleado(id);
    }

    // Uso programático del CacheManager: el dato recién traído de inventario reemplaza al guardado.
    private <T> T refrescar(String dominio, Long id, T valor) {
        domainCache.put(dominio, id, valor);
        return valor;
    }

    /** Para mostrar datos: vacío si inventario ya no lo ofrece (por ejemplo, fue dado de baja). */
    public Optional<ProductoResponseDTO> buscarProducto(Long id) {
        return buscar("Producto", id, inventarioClient::obtenerProducto);
    }

    public Optional<DepositoResponseDTO> buscarDeposito(Long id) {
        return buscar("Depósito", id, inventarioClient::obtenerDeposito);
    }

    public Optional<EmpleadoResponseDTO> buscarEmpleado(Long id) {
        return buscar("Empleado", id, inventarioClient::obtenerEmpleado);
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
