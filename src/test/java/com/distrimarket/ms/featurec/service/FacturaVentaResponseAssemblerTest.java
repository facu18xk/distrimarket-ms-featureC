package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.dto.DepositoResponseDTO;
import com.distrimarket.commons.dto.EmpleadoResponseDTO;
import com.distrimarket.commons.dto.FacturaVentaDetalleResponseDTO;
import com.distrimarket.commons.dto.FacturaVentaResponseDTO;
import com.distrimarket.commons.dto.PersonaResponseDTO;
import com.distrimarket.commons.dto.ProductoResponseDTO;
import com.distrimarket.commons.entity.Deposito;
import com.distrimarket.commons.entity.Empleado;
import com.distrimarket.commons.entity.FacturaVenta;
import com.distrimarket.ms.featurec.mapper.FacturaVentaMapper;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacturaVentaResponseAssemblerTest {

    @Mock
    private FacturaVentaMapper mapper;
    @Mock
    private InventarioService inventarioService;

    private FacturaVentaResponseAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new FacturaVentaResponseAssembler(mapper, inventarioService);
        lenient().when(inventarioService.buscarEmpleado(2L)).thenReturn(Optional.of(employee()));
        lenient().when(inventarioService.buscarDeposito(3L)).thenReturn(Optional.of(warehouse()));
        lenient().when(inventarioService.buscarProducto(7L)).thenReturn(Optional.of(product(7L, "Leche")));
        lenient().when(inventarioService.buscarProducto(8L)).thenReturn(Optional.of(product(8L, "Pan")));
    }

    @Test
    void completesProductNamesEmployeeAndWarehouseWithInventoryData() {
        FacturaVenta invoice = invoice(7L, 8L);

        FacturaVentaResponseDTO response = assembler.toResponse(invoice);

        assertEquals("Leche", response.getDetalles().get(0).getNombreProducto());
        assertEquals("Pan", response.getDetalles().get(1).getNombreProducto());
        assertEquals(2L, response.getEmpleado().get("id"));
        assertEquals("Cajero", response.getEmpleado().get("cargo"));
        assertEquals("Ana Gómez", ((Map<?, ?>) response.getEmpleado().get("persona")).get("nombreCompleto"));
        assertEquals(3L, response.getDeposito().get("id"));
        assertEquals("Depósito Central", response.getDeposito().get("nombre"));
    }

    @Test
    void eachDistinctProductEmployeeAndWarehouseIsQueriedOnlyOnceForAWholePage() {
        List<FacturaVenta> page = List.of(invoice(7L, 8L), invoice(7L), invoice(8L, 7L));

        List<FacturaVentaResponseDTO> responses = assembler.toResponses(page);

        assertEquals(3, responses.size());
        verify(inventarioService, times(1)).buscarProducto(7L);
        verify(inventarioService, times(1)).buscarProducto(8L);
        verify(inventarioService, times(1)).buscarEmpleado(2L);
        verify(inventarioService, times(1)).buscarDeposito(3L);
    }

    @Test
    void historicalInvoiceIsStillShownWhenInventoryNoLongerOffersItsData() {
        when(inventarioService.buscarProducto(9L)).thenReturn(Optional.empty());
        when(inventarioService.buscarEmpleado(2L)).thenReturn(Optional.empty());
        when(inventarioService.buscarDeposito(3L)).thenReturn(Optional.empty());

        FacturaVentaResponseDTO response = assembler.toResponse(invoice(9L, 7L));

        assertEquals(9L, response.getDetalles().get(0).getIdProducto());
        assertNull(response.getDetalles().get(0).getNombreProducto());
        assertEquals("Leche", response.getDetalles().get(1).getNombreProducto());
        assertEquals(Map.of("id", 2L), response.getEmpleado());
        assertEquals(Map.of("id", 3L), response.getDeposito());
    }

    @Test
    void whenInventoryIsDownTheInvoiceIsShownWithIdsAndInventoryIsNotCalledAgain() {
        when(inventarioService.buscarEmpleado(2L)).thenThrow(mock(FeignException.class));

        List<FacturaVentaResponseDTO> responses = assembler.toResponses(List.of(invoice(7L, 8L), invoice(7L)));

        assertEquals(2, responses.size());
        assertEquals(Map.of("id", 2L), responses.get(0).getEmpleado());
        assertEquals(Map.of("id", 3L), responses.get(0).getDeposito());
        assertNull(responses.get(0).getDetalles().get(0).getNombreProducto());
        verify(inventarioService, times(1)).buscarEmpleado(2L);
        verify(inventarioService, never()).buscarDeposito(anyLong());
        verify(inventarioService, never()).buscarProducto(anyLong());
    }

    @Test
    void invoiceWithoutDetailsOrReferencesIsHandled() {
        FacturaVenta invoice = new FacturaVenta();
        FacturaVentaResponseDTO mapped = new FacturaVentaResponseDTO();
        mapped.setDetalles(null);
        when(mapper.toDto(invoice)).thenReturn(mapped);

        FacturaVentaResponseDTO response = assembler.toResponse(invoice);

        assertNull(response.getEmpleado());
        assertNull(response.getDeposito());
        verify(inventarioService, never()).buscarProducto(anyLong());
    }

    /** Factura del empleado 2 en el depósito 3; el mapper solo entrega los ID de producto. */
    private FacturaVenta invoice(Long... productIds) {
        FacturaVenta invoice = new FacturaVenta();
        Empleado employee = new Empleado();
        employee.setId(2L);
        invoice.setEmpleado(employee);
        Deposito warehouse = new Deposito();
        warehouse.setId(3L);
        invoice.setDeposito(warehouse);

        FacturaVentaResponseDTO mapped = new FacturaVentaResponseDTO();
        List<FacturaVentaDetalleResponseDTO> details = new ArrayList<>();
        for (Long productId : productIds) {
            FacturaVentaDetalleResponseDTO detail = new FacturaVentaDetalleResponseDTO();
            detail.setIdProducto(productId);
            details.add(detail);
        }
        mapped.setDetalles(details);
        when(mapper.toDto(invoice)).thenReturn(mapped);
        return invoice;
    }

    private static ProductoResponseDTO product(Long id, String name) {
        ProductoResponseDTO product = new ProductoResponseDTO();
        product.setId(id);
        product.setNombre(name);
        return product;
    }

    private static EmpleadoResponseDTO employee() {
        PersonaResponseDTO person = new PersonaResponseDTO();
        person.setId(20L);
        person.setNombreCompleto("Ana Gómez");
        EmpleadoResponseDTO employee = new EmpleadoResponseDTO();
        employee.setId(2L);
        employee.setCargo("Cajero");
        employee.setEstado(true);
        employee.setPersona(person);
        return employee;
    }

    private static DepositoResponseDTO warehouse() {
        DepositoResponseDTO warehouse = new DepositoResponseDTO();
        warehouse.setId(3L);
        warehouse.setNombre("Depósito Central");
        warehouse.setEstado(true);
        return warehouse;
    }
}
