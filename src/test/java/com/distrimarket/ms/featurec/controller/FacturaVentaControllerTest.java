package com.distrimarket.ms.featurec.controller;

import com.distrimarket.commons.dto.EstadoFacturaVenta;
import com.distrimarket.commons.dto.FacturaVentaRequestDTO;
import com.distrimarket.commons.dto.FacturaVentaResponseDTO;
import com.distrimarket.commons.entity.FacturaVenta;
import com.distrimarket.ms.featurec.exception.GlobalExceptionHandler;
import com.distrimarket.ms.featurec.mapper.FacturaVentaMapper;
import com.distrimarket.ms.featurec.service.FacturaVentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FacturaVentaControllerTest {
    private final FacturaVentaService service = mock(FacturaVentaService.class);
    private final FacturaVentaMapper mapper = mock(FacturaVentaMapper.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FacturaVentaController(service, mapper))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .defaultRequest(get("/").accept("application/json"))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listReturns200ForAnEmptyPagedSearch() throws Exception {
        when(service.search(nullable(String.class), any(), any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mvc.perform(get("/facturas-venta").param("q", "no existe").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void listMapsTheCompleteInvoiceIncludingItsDetails() throws Exception {
        FacturaVenta invoice = new FacturaVenta();
        FacturaVentaResponseDTO response = new FacturaVentaResponseDTO();
        when(service.search(nullable(String.class), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(invoice), PageRequest.of(0, 10), 1));
        when(mapper.toDto(invoice)).thenReturn(response);

        mvc.perform(get("/facturas-venta").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1));

        verify(mapper).toDto(invoice);
    }

    @Test
    void createReturns201WithInvoiceAggregate() throws Exception {
        FacturaVenta entity = new FacturaVenta();
        when(mapper.toEntity(any(FacturaVentaRequestDTO.class))).thenReturn(entity);
        when(service.create(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(new FacturaVentaResponseDTO());

        mvc.perform(post("/facturas-venta").contentType("application/json").content(invoiceJson()))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidRequestBodyReturns400() throws Exception {
        mvc.perform(post("/facturas-venta").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void invoiceRoutesDelegateWithExpectedRestStatuses() throws Exception {
        FacturaVenta invoice = new FacturaVenta();
        when(service.getForRead(20L)).thenReturn(invoice);
        when(mapper.toDto(invoice)).thenReturn(new FacturaVentaResponseDTO());
        when(mapper.toEntity(any(FacturaVentaRequestDTO.class))).thenReturn(invoice);
        when(service.update(20L, invoice)).thenReturn(invoice);
        when(service.actualizarEstado(20L, EstadoFacturaVenta.ANULADA)).thenReturn(invoice);

        mvc.perform(get("/facturas-venta/20")).andExpect(status().isOk());
        mvc.perform(put("/facturas-venta/20").contentType("application/json").content(invoiceJson()))
                .andExpect(status().isOk());
        mvc.perform(patch("/facturas-venta/20").contentType("application/json")
                        .content("{\"estado\":\"ANULADA\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/facturas-venta/20")).andExpect(status().isNoContent());

        verify(service).delete(20L);
    }

    @Test
    void individualDetailRoutesAreNoLongerExposed() throws Exception {
        mvc.perform(get("/facturas-venta/20/detalles")).andExpect(status().isNotFound());
        mvc.perform(post("/facturas-venta/20/detalles").contentType("application/json")
                        .content("{\"idProducto\":6,\"cantidad\":2}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/facturas-venta/20/detalles/30").contentType("application/json")
                        .content("{\"idProducto\":6,\"cantidad\":2}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/facturas-venta/20/detalles/30")).andExpect(status().isNotFound());
    }

    private String invoiceJson() {
        return """
                {"numeroFactura":"001-001-0001234","idCliente":1,"idEmpleado":2,"idDeposito":3,"idMedioPago":4,"idTimbrado":5,"detalles":[{"idProducto":6,"cantidad":1}]}
                """;
    }
}
