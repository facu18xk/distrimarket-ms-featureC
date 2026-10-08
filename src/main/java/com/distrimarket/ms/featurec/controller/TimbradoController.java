package com.distrimarket.ms.featurec.controller;

import com.distrimarket.commons.entity.Timbrado;
import com.distrimarket.commons.dto.TimbradoPageResponseDTO;
import com.distrimarket.commons.dto.TimbradoCreateDTO;
import com.distrimarket.commons.dto.TimbradoDTO;
import com.distrimarket.ms.featurec.service.TimbradoService;
import com.distrimarket.ms.featurec.mapper.TimbradoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/timbrados")
@Tag(name = "Timbrados")
public class TimbradoController extends BaseController<TimbradoCreateDTO, TimbradoDTO, Timbrado> {
    private final TimbradoService timbradoService;

    public TimbradoController(TimbradoService service, TimbradoMapper mapper) {
        super(service, mapper);
        this.timbradoService = service;
    }

    @GetMapping
    @Operation(operationId = "listTimbrados", summary = "Listar timbrados")
    public TimbradoPageResponseDTO listar(
            @RequestParam(required = false, name = "q") String query,
            Pageable pageable) {
        var result = timbradoService.search(query, allowSorts(pageable,
                "id", "fechaCreacion", "fechaModificacion", "numeroTimbrado",
                "fechaInicio", "fechaVencimiento", "puntoExpedicion", "sucursal", "activo"));
        TimbradoPageResponseDTO response = new TimbradoPageResponseDTO();
        response.setPage(result.getNumber());
        response.setSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        response.setContent(result.map(mapper()::toDto).getContent());
        return response;
    }

    @Override
    @GetMapping("/{id}")
    @Operation(summary = "Obtener timbrado por ID")
    public TimbradoDTO get(@PathVariable Long id) {
        return super.get(id);
    }

    @Override
    @PostMapping
    @Operation(summary = "Crear timbrado")
    public ResponseEntity<TimbradoDTO> create(@Valid @RequestBody TimbradoCreateDTO request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    @Operation(summary = "Actualizar timbrado")
    public TimbradoDTO update(
            @PathVariable Long id,
            @Valid @RequestBody TimbradoCreateDTO request) {
        return super.update(id, request);
    }

    @Override
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar timbrado")
    public void delete(@PathVariable Long id) {
        super.delete(id);
    }
}
