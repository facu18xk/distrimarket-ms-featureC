package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.dto.ProductoResponseDTO;
import com.distrimarket.commons.dto.StockMovimientoRequestDTO;
import com.distrimarket.ms.featurec.client.InventarioClient;
import com.distrimarket.ms.featurec.exception.BusinessRuleException;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventarioServiceTest {

    @Mock
    private InventarioClient inventarioClient;
    @InjectMocks
    private InventarioService service;

    @Test
    void obtenerProductoDevuelveLoQueRespondeInventario() {
        ProductoResponseDTO producto = new ProductoResponseDTO();
        when(inventarioClient.obtenerProducto(7L)).thenReturn(producto);

        assertSame(producto, service.obtenerProducto(7L));
    }

    @Test
    void recursoInexistenteSeInformaConSuNombreEId() {
        when(inventarioClient.obtenerProducto(7L)).thenThrow(new ResourceNotFoundException("Recurso no encontrado"));
        when(inventarioClient.obtenerDeposito(3L)).thenThrow(new ResourceNotFoundException("Recurso no encontrado"));
        when(inventarioClient.obtenerEmpleado(2L)).thenThrow(new ResourceNotFoundException("Recurso no encontrado"));

        assertEquals("Producto con ID 7 no encontrado",
                assertThrows(ResourceNotFoundException.class, () -> service.obtenerProducto(7L)).getMessage());
        assertEquals("Depósito con ID 3 no encontrado",
                assertThrows(ResourceNotFoundException.class, () -> service.obtenerDeposito(3L)).getMessage());
        assertEquals("Empleado con ID 2 no encontrado",
                assertThrows(ResourceNotFoundException.class, () -> service.obtenerEmpleado(2L)).getMessage());
    }

    @Test
    void buscarDevuelveVacioCuandoInventarioYaNoOfreceElRecurso() {
        when(inventarioClient.obtenerProducto(7L)).thenThrow(new ResourceNotFoundException("Recurso no encontrado"));
        when(inventarioClient.obtenerDeposito(3L)).thenThrow(new ResourceNotFoundException("Recurso no encontrado"));

        assertTrue(service.buscarProducto(7L).isEmpty());
        assertTrue(service.buscarDeposito(3L).isEmpty());
    }

    @Test
    void buscarDevuelveElRecursoCuandoExiste() {
        ProductoResponseDTO producto = new ProductoResponseDTO();
        when(inventarioClient.obtenerProducto(7L)).thenReturn(producto);

        assertSame(producto, service.buscarProducto(7L).orElseThrow());
    }

    @Test
    void moverStockDeUnProductoDadoDeBajaEsReglaDeNegocioYNoUnNoEncontrado() {
        when(inventarioClient.aplicarMovimientos(any()))
                .thenThrow(new ResourceNotFoundException("Producto no encontrado con ID: 7"));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.moverStock(3L, Map.of(7L, 2), "Anulación de factura 001"));

        assertTrue(error.getMessage().contains("Producto no encontrado con ID: 7"));
    }

    @Test
    void moverStockEnviaUnDetallePorProductoYOmiteLosQueNoCambian() {
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        cantidades.put(7L, -2);
        cantidades.put(8L, 0);
        cantidades.put(9L, 1);

        service.moverStock(3L, cantidades, "Venta factura 001");

        ArgumentCaptor<StockMovimientoRequestDTO> enviado = ArgumentCaptor.forClass(StockMovimientoRequestDTO.class);
        verify(inventarioClient).aplicarMovimientos(enviado.capture());
        StockMovimientoRequestDTO movimiento = enviado.getValue();
        assertEquals(3L, movimiento.getIdDeposito());
        assertEquals("Venta factura 001", movimiento.getMotivo());
        assertEquals(com.distrimarket.commons.dto.OrigenMovimientoStock.VENTA, movimiento.getOrigen());
        assertEquals(2, movimiento.getDetalles().size());
        assertEquals(7L, movimiento.getDetalles().get(0).getIdProducto());
        assertEquals(-2, movimiento.getDetalles().get(0).getCantidad());
        assertEquals(9L, movimiento.getDetalles().get(1).getIdProducto());
        assertEquals(1, movimiento.getDetalles().get(1).getCantidad());
    }

    @Test
    void moverStockSinCambiosNoLlamaAInventario() {
        service.moverStock(3L, Map.of(7L, 0), "Modificación de factura 001");

        verify(inventarioClient, never()).aplicarMovimientos(any());
    }

    @Test
    void elRechazoDeInventarioLlegaTalCualAlServicioQueLoPidio() {
        when(inventarioClient.aplicarMovimientos(any())).thenThrow(new BusinessRuleException("Stock insuficiente"));

        assertThrows(BusinessRuleException.class, () -> service.moverStock(3L, Map.of(7L, -5), "Venta"));
    }
}
