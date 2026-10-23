package com.distrimarket.ms.featurec.client;

import com.distrimarket.commons.dto.DepositoResponseDTO;
import com.distrimarket.commons.dto.EmpleadoResponseDTO;
import com.distrimarket.commons.dto.ProductoResponseDTO;
import com.distrimarket.commons.dto.StockDepositoResponseDTO;
import com.distrimarket.commons.dto.StockMovimientoRequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * Acceso al microservicio de inventario, dueño de productos, depósitos, empleados y stock.
 * Se llama a la URL de {@code inventario.url}; si está vacía, el nombre se resuelve por Eureka.
 * Ventas no consulta esas tablas por su cuenta.
 */
@FeignClient(
        name = "${inventario.service-name}",
        url = "${inventario.url:}",
        path = "${inventario.context-path}",
        configuration = InventarioClientConfiguration.class)
public interface InventarioClient {

    @GetMapping("/productos/{id}")
    ProductoResponseDTO obtenerProducto(@PathVariable("id") Long id);

    @GetMapping("/depositos/{id}")
    DepositoResponseDTO obtenerDeposito(@PathVariable("id") Long id);

    @GetMapping("/empleados/{id}")
    EmpleadoResponseDTO obtenerEmpleado(@PathVariable("id") Long id);

    @PostMapping("/stock-depositos/movimientos")
    List<StockDepositoResponseDTO> aplicarMovimientos(@RequestBody StockMovimientoRequestDTO movimiento);
}
