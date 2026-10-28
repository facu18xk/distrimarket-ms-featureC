package com.distrimarket.ms.featurec.controller;

import com.distrimarket.commons.dto.PromocionDestinatariosDTO;
import com.distrimarket.commons.dto.PromocionPageResponseDTO;
import com.distrimarket.commons.dto.PromocionRequestDTO;
import com.distrimarket.commons.dto.PromocionResponseDTO;
import com.distrimarket.ms.featurec.service.PromocionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Promociones por producto: el vendedor las consulta; el ADMIN las crea, modifica y envía. */
@RestController
@RequestMapping("/promociones")
@Tag(name = "Ventas-Promociones")
@PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
public class PromocionController {

    private final PromocionService promocionService;

    public PromocionController(PromocionService promocionService) {
        this.promocionService = promocionService;
    }

    @GetMapping
    @Operation(operationId = "listarPromociones")
    public PromocionPageResponseDTO listar(@RequestParam(required = false, name = "q") String q,
                                           @Parameter(hidden = true) Pageable pageable) {
        var result = promocionService.listar(q, pageable);
        PromocionPageResponseDTO response = new PromocionPageResponseDTO();
        response.setPageNumber(result.getNumber());
        response.setPageSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        response.setIsFirst(result.isFirst());
        response.setIsLast(result.isLast());
        response.setContent(result.getContent());
        return response;
    }

    @GetMapping("/{id}")
    @Operation(operationId = "obtenerPromocion")
    public PromocionResponseDTO obtener(@PathVariable Long id) {
        return promocionService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "crearPromocion")
    public PromocionResponseDTO crear(@Valid @RequestBody PromocionRequestDTO request) {
        return promocionService.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "actualizarPromocion")
    public PromocionResponseDTO actualizar(@PathVariable Long id, @Valid @RequestBody PromocionRequestDTO request) {
        return promocionService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "eliminarPromocion")
    public void eliminar(@PathVariable Long id) {
        promocionService.eliminar(id);
    }

    @GetMapping("/{id}/destinatarios")
    @Operation(operationId = "destinatariosPromocion")
    public PromocionDestinatariosDTO destinatarios(@PathVariable Long id) {
        return promocionService.destinatarios(id);
    }

    @PostMapping("/{id}/enviar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "enviarPromocion")
    public PromocionResponseDTO enviar(@PathVariable Long id) {
        return promocionService.enviarAhora(id);
    }
}
