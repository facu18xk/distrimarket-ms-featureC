package com.distrimarket.ms.featurec.controller;

import com.distrimarket.commons.dto.ContactoMensajePageResponseDTO;
import com.distrimarket.commons.dto.ContactoMensajeRequestDTO;
import com.distrimarket.commons.dto.ContactoMensajeResponseDTO;
import com.distrimarket.ms.featurec.service.SoporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Soporte. El formulario de contacto es público (se usa desde el login, sin sesión): solo guarda
 * el mensaje y responde 202; un job lo reenvía por correo. La bandeja es solo para ADMIN.
 */
@RestController
@RequestMapping("/soporte")
@Tag(name = "Ventas-Soporte")
public class SoporteController {

    private final SoporteService soporteService;

    public SoporteController(SoporteService soporteService) {
        this.soporteService = soporteService;
    }

    @PostMapping("/contacto")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "enviarMensajeSoporte")
    public ContactoMensajeResponseDTO contacto(@Valid @RequestBody ContactoMensajeRequestDTO request) {
        return soporteService.registrar(request);
    }

    @GetMapping("/mensajes")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "listarMensajesSoporte")
    public ContactoMensajePageResponseDTO listar(
            @RequestParam(required = false) Boolean atendido,
            @Parameter(hidden = true) Pageable pageable) {
        var result = soporteService.listar(atendido, pageable);
        ContactoMensajePageResponseDTO response = new ContactoMensajePageResponseDTO();
        response.setPageNumber(result.getNumber());
        response.setPageSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        response.setIsFirst(result.isFirst());
        response.setIsLast(result.isLast());
        response.setContent(result.getContent());
        return response;
    }

    @PatchMapping("/mensajes/{id}/atendido")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "marcarMensajeAtendido")
    public ContactoMensajeResponseDTO atendido(@PathVariable Long id) {
        return soporteService.marcarAtendido(id);
    }
}
