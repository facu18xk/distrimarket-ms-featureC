package com.distrimarket.ms.featurec.controller;

import com.distrimarket.commons.entity.FacturaVenta;
import com.distrimarket.commons.dto.FacturaVentaEstadoRequestDTO;
import com.distrimarket.commons.dto.FacturaVentaPageResponseDTO;
import com.distrimarket.commons.dto.FacturaVentaRequestDTO;
import com.distrimarket.commons.dto.FacturaVentaResponseDTO;
import com.distrimarket.ms.featurec.service.FacturaVentaService;
import com.distrimarket.ms.featurec.mapper.FacturaVentaMapper;
import com.distrimarket.ms.featurec.config.SearchFilterSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/facturas-venta")
@Tag(name = "Facturas-Venta")
public class FacturaVentaController
        extends BaseController<FacturaVentaRequestDTO, FacturaVentaResponseDTO, FacturaVenta> {

    private final FacturaVentaService facturaVentaService;
    private final FacturaVentaMapper facturaVentaMapper;

    public FacturaVentaController(
            FacturaVentaService service,
            FacturaVentaMapper mapper) {
        super(service, mapper);
        this.facturaVentaService = service;
        this.facturaVentaMapper = mapper;
    }

    @GetMapping
    @Operation(operationId = "listFacturasVenta")
    public FacturaVentaPageResponseDTO listar(
            @RequestBody(required = false) Map<String, Object> filter,
            @Parameter(hidden = true) @RequestParam(required = false, name = "q") String legacyQuery,
            @Parameter(hidden = true) Pageable pageable) {
        String query = SearchFilterSupport.query(filter, legacyQuery);
        var result = facturaVentaService.search(query, pageable);
        FacturaVentaPageResponseDTO response = new FacturaVentaPageResponseDTO();
        response.setPageNumber(result.getNumber());
        response.setPageSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        response.setIsFirst(result.isFirst());
        response.setIsLast(result.isLast());
        response.setContent(result.map(facturaVentaMapper::toDto).getContent());
        return response;
    }

    @Override
    @GetMapping("/{id}")
    public FacturaVentaResponseDTO get(@PathVariable Long id) {
        return super.get(id);
    }

    @Override
    @PostMapping
    public ResponseEntity<FacturaVentaResponseDTO> create(
            @Valid @RequestBody FacturaVentaRequestDTO request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    @Operation(
            description = "Reemplaza la colección completa de detalles. Las líneas existentes se reconocen por producto y orden de aparición; las omitidas se quitan y ajustan el stock.")
    public FacturaVentaResponseDTO update(
            @PathVariable Long id,
            @Valid @RequestBody FacturaVentaRequestDTO request) {
        return super.update(id, request);
    }

    @PatchMapping("/{facturaId}")
    public FacturaVentaResponseDTO actualizarEstado(
            @PathVariable Long facturaId,
            @Valid @RequestBody FacturaVentaEstadoRequestDTO request) {
        return facturaVentaMapper.toDto(facturaVentaService.actualizarEstado(facturaId, request.getEstado()));
    }

    @Override
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            description = "Conserva sus detalles y repone el stock cuando la factura estaba emitida.")
    public void delete(@PathVariable Long id) {
        super.delete(id);
    }
}
