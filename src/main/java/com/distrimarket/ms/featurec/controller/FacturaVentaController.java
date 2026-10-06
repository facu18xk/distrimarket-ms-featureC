package com.distrimarket.ms.featurec.controller;

import com.distrimarket.commons.entity.FacturaVenta;
import com.distrimarket.commons.dto.FacturaVentaDetalleResponseDTO;
import com.distrimarket.commons.dto.FacturaVentaDetallePageResponseDTO;
import com.distrimarket.commons.dto.FacturaVentaDetalleRequestDTO;
import com.distrimarket.commons.dto.FacturaVentaEstadoRequestDTO;
import com.distrimarket.commons.dto.FacturaVentaPageResponseDTO;
import com.distrimarket.commons.dto.FacturaVentaRequestDTO;
import com.distrimarket.commons.dto.FacturaVentaResponseDTO;
import com.distrimarket.ms.featurec.service.FacturaVentaService;
import com.distrimarket.ms.featurec.mapper.FacturaVentaMapper;
import io.swagger.v3.oas.annotations.Operation;
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

@RestController
@RequestMapping("/facturas-venta")
@Tag(name = "Facturas-Venta")
public class FacturaVentaController
        extends BaseController<FacturaVentaRequestDTO, FacturaVentaResponseDTO, FacturaVenta> {

    private final FacturaVentaService facturaVentaService;

    public FacturaVentaController(
            FacturaVentaService service,
            FacturaVentaMapper mapper) {
        super(service, mapper);
        this.facturaVentaService = service;
    }

    @GetMapping
    @Operation(operationId = "listFacturasVenta", summary = "Listar facturas de venta")
    public FacturaVentaPageResponseDTO listar(
            @RequestParam(required = false, name = "q") String query,
            Pageable pageable) {
        var result = facturaVentaService.search(query, pageable);
        requireResults(result, "Facturas de venta");
        FacturaVentaPageResponseDTO response = new FacturaVentaPageResponseDTO();
        response.setPage(result.getNumber());
        response.setSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        response.setContent(result.map(mapper()::toDto).getContent());
        return response;
    }

    @Override
    @GetMapping("/{id}")
    @Operation(summary = "Obtener factura")
    public FacturaVentaResponseDTO get(@PathVariable Long id) {
        return super.get(id);
    }

    @GetMapping("/{facturaId}/detalles")
    @Operation(summary = "Listar detalles de una factura de venta")
    public FacturaVentaDetallePageResponseDTO listarDetalles(
            @PathVariable("facturaId") Long facturaId,
            @RequestParam(required = false, name = "q") String query,
            Pageable pageable) {
        var result = facturaVentaService.listarDetalles(facturaId, query, pageable);
        requireResults(result, "Detalles de factura de venta");
        FacturaVentaDetallePageResponseDTO response = new FacturaVentaDetallePageResponseDTO();
        response.setPage(result.getNumber());
        response.setSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        response.setContent(result.getContent());
        return response;
    }

    @GetMapping("/{facturaId}/detalles/{detalleId}")
    @Operation(summary = "Obtener un detalle de una factura de venta")
    public FacturaVentaDetalleResponseDTO obtenerDetalle(
            @PathVariable Long facturaId,
            @PathVariable Long detalleId) {
        return facturaVentaService.obtenerDetalle(facturaId, detalleId);
    }

    @Override
    @PostMapping
    @Operation(summary = "Crear cabecera y detalles")
    public ResponseEntity<FacturaVentaResponseDTO> create(
            @Valid @RequestBody FacturaVentaRequestDTO request) {
        return super.create(request);
    }

    @PostMapping("/{facturaId}/detalles")
    @Operation(summary = "Agregar detalle")
    public ResponseEntity<FacturaVentaDetalleResponseDTO> agregarDetalle(
            @PathVariable Long facturaId,
            @Valid @RequestBody FacturaVentaDetalleRequestDTO request) {
        FacturaVentaDetalleResponseDTO detalle = facturaVentaService.agregarDetalle(facturaId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(detalle);
    }

    @Override
    @PutMapping("/{id}")
    @Operation(summary = "Actualizar cabecera y detalles")
    public FacturaVentaResponseDTO update(
            @PathVariable Long id,
            @Valid @RequestBody FacturaVentaRequestDTO request) {
        return super.update(id, request);
    }

    @PutMapping("/{facturaId}/detalles/{detalleId}")
    @Operation(summary = "Actualizar detalle")
    public FacturaVentaDetalleResponseDTO actualizarDetalle(
            @PathVariable Long facturaId,
            @PathVariable Long detalleId,
            @Valid @RequestBody FacturaVentaDetalleRequestDTO request) {
        return facturaVentaService.actualizarDetalle(facturaId, detalleId, request);
    }

    @PatchMapping("/{facturaId}")
    @Operation(summary = "Actualizar estado de cabecera")
    public FacturaVentaResponseDTO actualizarEstado(
            @PathVariable Long facturaId,
            @Valid @RequestBody FacturaVentaEstadoRequestDTO request) {
        return mapper().toDto(facturaVentaService.actualizarEstado(facturaId, request.getEstado()));
    }

    @Override
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar cabecera")
    public void delete(@PathVariable Long id) {
        super.delete(id);
    }
}
