package com.distrimarket.ms.featurec.controller;

import com.distrimarket.commons.dto.TimbradoCreateDTO;
import com.distrimarket.commons.dto.TimbradoDTO;
import com.distrimarket.commons.entity.Timbrado;
import com.distrimarket.ms.featurec.exception.GlobalExceptionHandler;
import com.distrimarket.ms.featurec.mapper.TimbradoMapper;
import com.distrimarket.ms.featurec.service.TimbradoService;
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

class TimbradoControllerTest {
    private final TimbradoService service = mock(TimbradoService.class);
    private final TimbradoMapper mapper = mock(TimbradoMapper.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TimbradoController(service, mapper))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .defaultRequest(get("/").accept("application/json"))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listIsPagedAndReturnsEmptyCollectionWith200() throws Exception {
        when(service.search(nullable(String.class), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mvc.perform(get("/timbrados").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void createReturns201AndDeleteReturns204() throws Exception {
        when(service.crear(any(TimbradoCreateDTO.class))).thenReturn(new TimbradoDTO());

        mvc.perform(post("/timbrados").contentType("application/json")
                        .content("""
                                {"numeroTimbrado":"12345678","fechaInicio":"2026-01-01","fechaVencimiento":"2026-12-31"}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(delete("/timbrados/7")).andExpect(status().isNoContent());
        verify(service).eliminar(7L);
    }

    @Test
    void invalidDatesAreReturnedAsStructured400() throws Exception {
        mvc.perform(post("/timbrados").contentType("application/json")
                        .content("{\"numeroTimbrado\":\"123\",\"fechaInicio\":\"no-es-fecha\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getAndUpdateDelegateAndReturn200() throws Exception {
        when(service.obtener(7L)).thenReturn(new TimbradoDTO());
        when(service.actualizar(eq(7L), any(TimbradoCreateDTO.class))).thenReturn(new TimbradoDTO());

        mvc.perform(get("/timbrados/7")).andExpect(status().isOk());
        mvc.perform(put("/timbrados/7").contentType("application/json")
                        .content("""
                                {"numeroTimbrado":"12345678","fechaInicio":"2026-01-01","fechaVencimiento":"2026-12-31"}
                                """))
                .andExpect(status().isOk());

        verify(service).actualizar(eq(7L), any(TimbradoCreateDTO.class));
    }
}
