package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.dto.ContactoMensajeRequestDTO;
import com.distrimarket.commons.dto.ContactoMensajeResponseDTO;
import com.distrimarket.commons.entity.ContactoMensaje;
import com.distrimarket.commons.service.Paginas;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.repository.ContactoMensajeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * Mensajes de contacto a soporte. Guardarlo es inmediato (responde 202); el correo lo envía
 * después el job {@code SoporteCorreoJob}, así el formulario no espera al servidor SMTP.
 */
@Slf4j
@Service
public class SoporteService {

    /** Campos por los que la UI puede ordenar (más "id" y el timestamp real de la entidad). */
    private static final Set<String> ORDENES_PERMITIDOS = Set.of(
            "id", "fechaCreacion", "atendido", "nombreRemitente", "correoRemitente",
            "asunto", "correoEnviado");
    private static final Sort ORDEN_POR_DEFECTO = Sort.by(Sort.Direction.DESC, "id");

    private final ContactoMensajeRepository repository;

    public SoporteService(ContactoMensajeRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ContactoMensajeResponseDTO registrar(ContactoMensajeRequestDTO request) {
        ContactoMensaje mensaje = ContactoMensaje.builder()
                .nombreRemitente(request.getNombre().trim())
                .correoRemitente(request.getCorreo().trim())
                .asunto(request.getAsunto().trim())
                .mensaje(request.getMensaje().trim())
                .build();
        ContactoMensaje guardado = repository.save(mensaje);
        log.info("Mensaje de soporte {} recibido de {} ({})", guardado.getId(), guardado.getCorreoRemitente(), guardado.getAsunto());
        return toDto(guardado);
    }

    @Transactional(readOnly = true)
    public Page<ContactoMensajeResponseDTO> listar(Boolean atendido, Pageable pageable) {
        // Los más nuevos primero, salvo que se pida otro orden; siempre con desempate por id.
        Pageable pagina = Paginas.conOrdenPermitido(pageable, ORDENES_PERMITIDOS, ORDEN_POR_DEFECTO);
        Page<ContactoMensaje> mensajes = atendido == null ? repository.findAll(pagina) : repository.findByAtendido(atendido, pagina);
        return mensajes.map(this::toDto);
    }

    @Transactional
    public ContactoMensajeResponseDTO marcarAtendido(Long id) {
        ContactoMensaje mensaje = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Mensaje de soporte", id));
        mensaje.setAtendido(true);
        return toDto(repository.save(mensaje));
    }

    /** Lote de mensajes que el job tiene que reenviar por correo. */
    @Transactional(readOnly = true)
    public List<ContactoMensaje> pendientesDeCorreo(int lote, int maxIntentos) {
        return repository.findPendientesDeCorreo(maxIntentos, PageRequest.of(0, lote));
    }

    @Transactional
    public void registrarResultadoCorreo(Long id, String error) {
        repository.findById(id).ifPresent(mensaje -> {
            if (error == null) {
                mensaje.setCorreoEnviado(true);
                mensaje.setFechaCorreo(LocalDateTime.now());
                mensaje.setErrorCorreo(null);
            } else {
                mensaje.setIntentosCorreo(mensaje.getIntentosCorreo() + 1);
                mensaje.setErrorCorreo(error.length() > 500 ? error.substring(0, 500) : error);
            }
            repository.save(mensaje);
        });
    }

    private ContactoMensajeResponseDTO toDto(ContactoMensaje m) {
        ContactoMensajeResponseDTO dto = new ContactoMensajeResponseDTO();
        dto.setId(m.getId());
        dto.setNombre(m.getNombreRemitente());
        dto.setCorreo(m.getCorreoRemitente());
        dto.setAsunto(m.getAsunto());
        dto.setMensaje(m.getMensaje());
        dto.setFechaEnvio(m.getFechaEnvio() == null ? null : m.getFechaEnvio().atZone(ZoneId.systemDefault()).toOffsetDateTime());
        dto.setAtendido(m.getAtendido());
        dto.setCorreoEnviado(m.getCorreoEnviado());
        dto.setErrorCorreo(m.getErrorCorreo());
        return dto;
    }
}
