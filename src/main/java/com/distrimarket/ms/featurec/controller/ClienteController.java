package com.distrimarket.ms.featurec.controller;

import com.distrimarket.commons.entity.Cliente;
import com.distrimarket.commons.dto.ClientePageResponseDTO;
import com.distrimarket.commons.dto.ClienteRequestDTO;
import com.distrimarket.commons.dto.ClienteResponseDTO;
import com.distrimarket.ms.featurec.service.ClienteService;
import com.distrimarket.ms.featurec.mapper.ClienteMapper;
import com.distrimarket.ms.featurec.config.SearchFilterSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/clientes")
@Tag(name = "Clientes")
public class ClienteController extends BaseController<ClienteRequestDTO, ClienteResponseDTO, Cliente> {

    private final ClienteService clienteService;

    public ClienteController(ClienteService service, ClienteMapper mapper) {
        super(service, mapper);
        this.clienteService = service;
    }

    @GetMapping
    @Operation(operationId = "listClientes")
    public ClientePageResponseDTO listar(
            @RequestBody(required = false) Map<String, Object> filter,
            @Parameter(hidden = true) @RequestParam(required = false, name = "q") String legacyQuery,
            @Parameter(hidden = true) Pageable pageable) {
        String query = SearchFilterSupport.query(filter, legacyQuery);
        var result = clienteService.search(query, allowSorts(pageable,
                "id", "fechaCreacion", "fechaModificacion", "estado",
                "persona.nombreCompleto", "persona.tipoPersona", "persona.ci", "persona.ruc"));
        ClientePageResponseDTO response = new ClientePageResponseDTO();
        response.setPageNumber(result.getNumber());
        response.setPageSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        response.setIsFirst(result.isFirst());
        response.setIsLast(result.isLast());
        response.setContent(result.map(mapper()::toDto).getContent());
        return response;
    }

    @Override
    @GetMapping("/{id}")
    public ClienteResponseDTO get(@PathVariable Long id) {
        return super.get(id);
    }

    @Override
    @PostMapping
    public ResponseEntity<ClienteResponseDTO> create(@Valid @RequestBody ClienteRequestDTO request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    public ClienteResponseDTO update(
            @PathVariable Long id,
            @Valid @RequestBody ClienteRequestDTO request) {
        return super.update(id, request);
    }

    @Override
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        super.delete(id);
    }
}
