package com.distrimarket.ms.featurec.controller;

import com.distrimarket.commons.dto.ClientePageResponseDTO;
import com.distrimarket.commons.dto.ClienteRequestDTO;
import com.distrimarket.commons.dto.ClienteResponseDTO;
import com.distrimarket.commons.entity.Cliente;
import com.distrimarket.ms.featurec.exception.GlobalExceptionHandler;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.mapper.ClienteMapper;
import com.distrimarket.ms.featurec.service.ClienteService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;

class ClienteControllerTest {
    private final ClienteService service = mock(ClienteService.class);
    private final ClienteMapper mapper = mock(ClienteMapper.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ClienteController(service, mapper))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .defaultRequest(get("/").accept("application/json"))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listReturnsPaginatedContentAndEmptySearchIsStillSuccessful() throws Exception {
        Cliente cliente = new Cliente();
        ClienteResponseDTO dto = new ClienteResponseDTO();
        when(service.search(anyString(), any())).thenReturn(new PageImpl<>(
                List.of(cliente), PageRequest.of(0, 10), 1));
        when(mapper.toDto(cliente)).thenReturn(dto);

        mvc.perform(get("/clientes").param("q", "Ana").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content").isArray());

        when(service.search(nullable(String.class), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        mvc.perform(get("/clientes").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void createReturns201AndInvalidPayloadReturnsStandard400() throws Exception {
        Cliente entity = new Cliente();
        when(mapper.toEntity(any(ClienteRequestDTO.class))).thenReturn(entity);
        when(service.create(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(new ClienteResponseDTO());

        mvc.perform(post("/clientes").contentType("application/json")
                        .content("""
                                {"persona":{"tipoPersona":"FISICA","nombreCompleto":"Ana Pérez"}}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(post("/clientes").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    void missingClientReturns404AndDeleteReturns204() throws Exception {
        when(service.getForRead(55L)).thenThrow(new ResourceNotFoundException("Cliente", 55L));

        mvc.perform(get("/clientes/55"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mvc.perform(delete("/clientes/55")).andExpect(status().isNoContent());
        verify(service).delete(55L);
    }

    @Test
    void getAndUpdateReturn200AndDelegateToService() throws Exception {
        Cliente entity = new Cliente();
        when(service.getForRead(8L)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(new ClienteResponseDTO());
        when(mapper.toEntity(any(ClienteRequestDTO.class))).thenReturn(entity);
        when(service.update(8L, entity)).thenReturn(entity);

        mvc.perform(get("/clientes/8")).andExpect(status().isOk());
        mvc.perform(put("/clientes/8").contentType("application/json")
                        .content("""
                                {"persona":{"tipoPersona":"FISICA","nombreCompleto":"Ana Pérez"}}
                                """))
                .andExpect(status().isOk());

        verify(service).update(8L, entity);
    }
}
