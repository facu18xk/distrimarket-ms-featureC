package com.distrimarket.ms.featurec.controller;

import com.distrimarket.commons.dto.MedioPagoDTO;
import com.distrimarket.commons.dto.PageResponseDTO;
import com.distrimarket.commons.entity.MedioPago;
import com.distrimarket.ms.featurec.mapper.MedioPagoMapper;
import com.distrimarket.ms.featurec.service.MedioPagoService;
import com.distrimarket.ms.featurec.config.SearchFilterSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.Map;

@RestController
@RequestMapping("/medios-pago")
@Tag(name = "Medios de pago")
public class MedioPagoController extends BaseController<MedioPagoDTO, MedioPagoDTO, MedioPago> {

    private final MedioPagoService medioPagoService;

    public MedioPagoController(MedioPagoService service, MedioPagoMapper mapper) {
        super(service, mapper);
        this.medioPagoService = service;
    }

    @GetMapping
    @Operation(operationId = "listMediosPago")
    public PageResponseDTO<MedioPagoDTO> listar(
            @RequestBody(required = false) Map<String, Object> filter,
            @Parameter(hidden = true) @RequestParam(required = false, name = "q") String legacyQuery,
            @Parameter(hidden = true) Pageable pageable) {
        String query = SearchFilterSupport.query(filter, legacyQuery);
        var result = medioPagoService.search(query, allowSorts(pageable,
                "id", "nombre", "activo", "fechaCreacion", "fechaModificacion"));
        return PageResponseDTO.from(result.map(mapper()::toDto));
    }

    @Override
    @GetMapping("/{id}")
    public MedioPagoDTO get(@PathVariable Long id) {
        return super.get(id);
    }

    @Override
    @PostMapping
    public ResponseEntity<MedioPagoDTO> create(@RequestBody MedioPagoDTO request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    public MedioPagoDTO update(
            @PathVariable Long id,
            @RequestBody MedioPagoDTO request) {
        return super.update(id, request);
    }

    @Override
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        super.delete(id);
    }
}
