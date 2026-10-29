package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.dto.ContactoMensajeRequestDTO;
import com.distrimarket.commons.dto.ContactoMensajeResponseDTO;
import com.distrimarket.commons.entity.ContactoMensaje;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.repository.ContactoMensajeRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SoporteServiceTest {

    private final ContactoMensajeRepository repository = mock(ContactoMensajeRepository.class);
    private final SoporteService service = new SoporteService(repository);

    private static ContactoMensaje mensaje() {
        ContactoMensaje m = ContactoMensaje.builder()
                .nombreRemitente("Ana").correoRemitente("ana@example.test").asunto("Ayuda").mensaje("Hola").build();
        m.setId(5L);
        return m;
    }

    @Test
    void registrarGuardaSinEspaciosYPendienteDeCorreo() {
        when(repository.save(any(ContactoMensaje.class))).thenAnswer(i -> i.getArgument(0));
        ContactoMensajeRequestDTO pedido = new ContactoMensajeRequestDTO();
        pedido.setNombre("  Ana ");
        pedido.setCorreo(" ana@example.test ");
        pedido.setAsunto(" Ayuda ");
        pedido.setMensaje(" No puedo entrar ");

        ContactoMensajeResponseDTO dto = service.registrar(pedido);

        assertThat(dto.getNombre()).isEqualTo("Ana");
        assertThat(dto.getCorreo()).isEqualTo("ana@example.test");
        assertThat(dto.getMensaje()).isEqualTo("No puedo entrar");
        assertThat(dto.getCorreoEnviado()).isFalse();
        assertThat(dto.getAtendido()).isFalse();
    }

    @Test
    void correoEnviadoQuedaMarcado() {
        ContactoMensaje m = mensaje();
        m.setErrorCorreo("error anterior");
        when(repository.findById(5L)).thenReturn(Optional.of(m));

        service.registrarResultadoCorreo(5L, null);

        assertThat(m.getCorreoEnviado()).isTrue();
        assertThat(m.getFechaCorreo()).isNotNull();
        assertThat(m.getErrorCorreo()).isNull();
    }

    @Test
    void correoFallidoSumaIntentoYGuardaElErrorRecortado() {
        ContactoMensaje m = mensaje();
        when(repository.findById(5L)).thenReturn(Optional.of(m));

        service.registrarResultadoCorreo(5L, "x".repeat(800));

        assertThat(m.getCorreoEnviado()).isFalse();
        assertThat(m.getIntentosCorreo()).isEqualTo(1);
        assertThat(m.getErrorCorreo()).hasSize(500);
    }

    @Test
    void marcarAtendidoDeUnMensajeInexistenteFalla() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.marcarAtendido(9L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
