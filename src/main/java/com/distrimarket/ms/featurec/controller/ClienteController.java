package com.distrimarket.ms.featurec.controller;

import com.distrimarket.commons.entity.Cliente;
import com.distrimarket.commons.dto.ClientePageResponseDTO;
import com.distrimarket.commons.dto.ClienteRequestDTO;
import com.distrimarket.commons.dto.ClienteResponseDTO;
import com.distrimarket.ms.featurec.service.ClienteService;
import com.distrimarket.ms.featurec.mapper.ClienteMapper;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(operationId = "listClientes", summary = "Listar clientes")
    public ClientePageResponseDTO listar(
            @RequestParam(required = false, name = "q") String query,
            Pageable pageable) {
        var result = clienteService.search(query, allowSorts(pageable,
                "id", "fechaCreacion", "fechaModificacion", "estado",
                "persona.nombreCompleto", "persona.tipoPersona", "persona.ci", "persona.ruc"));
        ClientePageResponseDTO response = new ClientePageResponseDTO();
        response.setPage(result.getNumber());
        response.setSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        response.setContent(result.map(mapper()::toDto).getContent());
        return response;
    }

    @Override
    @GetMapping("/{id}")
    @Operation(summary = "Obtener cliente por ID")
    public ClienteResponseDTO get(@PathVariable Long id) {
        return super.get(id);
    }

    @Override
    @PostMapping
    @Operation(summary = "Crear cliente")
    public ResponseEntity<ClienteResponseDTO> create(@Valid @RequestBody ClienteRequestDTO request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    @Operation(summary = "Actualizar cliente")
    public ClienteResponseDTO update(
            @PathVariable Long id,
            @Valid @RequestBody ClienteRequestDTO request) {
        return super.update(id, request);
    }

    @Override
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar cliente")
    public void delete(@PathVariable Long id) {
        super.delete(id);
    }
}
