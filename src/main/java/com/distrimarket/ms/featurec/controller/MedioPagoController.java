package com.distrimarket.ms.featurec.controller;

import com.distrimarket.commons.dto.MedioPagoDTO;
import com.distrimarket.commons.dto.MedioPagoPageResponseDTO;
import com.distrimarket.commons.dto.MedioPagoRequestDTO;
import com.distrimarket.commons.entity.MedioPago;
import com.distrimarket.ms.featurec.mapper.MedioPagoMapper;
import com.distrimarket.ms.featurec.service.MedioPagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/medios-pago")
@Tag(name = "Medios de pago")
@PreAuthorize("hasRole('ADMIN')")
public class MedioPagoController extends BaseController<MedioPagoRequestDTO, MedioPagoDTO, MedioPago> {

    private final MedioPagoService medioPagoService;

    public MedioPagoController(MedioPagoService service, MedioPagoMapper mapper) {
        super(service, mapper);
        this.medioPagoService = service;
    }

    // Consultar: también vendedores y compradores (lo usan al facturar). Modificar: solo ADMIN.
    @GetMapping
    @Operation(operationId = "listarMediosPago")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR', 'COMPRADOR')")
    public MedioPagoPageResponseDTO listar(
            @RequestParam(required = false, name = "q") String query,
            @Parameter(hidden = true) Pageable pageable) {
        var result = medioPagoService.buscar(query, allowSorts(pageable,
                "id", "nombre", "activo", "fechaCreacion", "fechaModificacion"));
        MedioPagoPageResponseDTO response = new MedioPagoPageResponseDTO();
        response.setPageNumber(result.getNumber());
        response.setPageSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        response.setIsFirst(result.isFirst());
        response.setIsLast(result.isLast());
        response.setContent(result.getContent());
        return response;
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR', 'COMPRADOR')")
    public MedioPagoDTO get(@PathVariable Long id) {
        return super.get(id);
    }

    @Override
    @PostMapping
    public ResponseEntity<MedioPagoDTO> create(@Valid @RequestBody MedioPagoRequestDTO request) {
        return super.create(request);
    }

    @Override
    @PutMapping("/{id}")
    public MedioPagoDTO update(
            @PathVariable Long id,
            @Valid @RequestBody MedioPagoRequestDTO request) {
        return super.update(id, request);
    }

    @Override
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        super.delete(id);
    }
}
