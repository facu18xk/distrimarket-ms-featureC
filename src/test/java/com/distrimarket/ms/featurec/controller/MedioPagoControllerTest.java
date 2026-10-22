package com.distrimarket.ms.featurec.controller;

import com.distrimarket.commons.dto.MedioPagoDTO;
import com.distrimarket.commons.dto.MedioPagoRequestDTO;
import com.distrimarket.commons.entity.MedioPago;
import com.distrimarket.ms.featurec.exception.GlobalExceptionHandler;
import com.distrimarket.ms.featurec.mapper.MedioPagoMapper;
import com.distrimarket.ms.featurec.service.MedioPagoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MedioPagoControllerTest {
    private final MedioPagoService service = mock(MedioPagoService.class);
    private final MedioPagoMapper mapper = mock(MedioPagoMapper.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MedioPagoController(service, mapper))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .defaultRequest(get("/").accept("application/json"))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listIsPagedAndReturnsEmptyCollectionWith200() throws Exception {
        when(service.search(nullable(String.class), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mvc.perform(get("/medios-pago").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.isFirst").value(true))
                .andExpect(jsonPath("$.isLast").value(true))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void createReturns201AndDeleteReturns204() throws Exception {
        when(service.crear(any(MedioPagoRequestDTO.class))).thenReturn(new MedioPagoDTO());

        mvc.perform(post("/medios-pago").contentType("application/json")
                        .content("{\"nombre\":\"Efectivo\"}"))
                .andExpect(status().isCreated());

        mvc.perform(delete("/medios-pago/7")).andExpect(status().isNoContent());
        verify(service).eliminar(7L);
    }

    @Test
    void blankNameIsReturnedAsStructured400() throws Exception {
        when(service.crear(any(MedioPagoRequestDTO.class))).thenThrow(
                new IllegalArgumentException("El nombre del medio de pago es obligatorio."));

        mvc.perform(post("/medios-pago").contentType("application/json")
                        .content("{\"nombre\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getAndUpdateDelegateAndReturn200() throws Exception {
        when(service.obtener(7L)).thenReturn(new MedioPagoDTO());
        when(service.actualizar(eq(7L), any(MedioPagoRequestDTO.class))).thenReturn(new MedioPagoDTO());

        mvc.perform(get("/medios-pago/7")).andExpect(status().isOk());
        mvc.perform(put("/medios-pago/7").contentType("application/json")
                        .content("{\"nombre\":\"Tarjeta\"}"))
                .andExpect(status().isOk());

        verify(service).actualizar(eq(7L), any(MedioPagoRequestDTO.class));
    }
}
