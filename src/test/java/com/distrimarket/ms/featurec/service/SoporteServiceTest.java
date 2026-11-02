package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.dto.ContactoMensajeRequestDTO;
import com.distrimarket.commons.dto.ContactoMensajeResponseDTO;
import com.distrimarket.commons.entity.ContactoMensaje;
import com.distrimarket.ms.featurec.exception.ResourceNotFoundException;
import com.distrimarket.ms.featurec.repository.ContactoMensajeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    void listarSinOrdenUsaIdDescendente() {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        service.listar(null, PageRequest.of(0, 10));

        Pageable orden = capturarOrdenDeListado();
        assertThat(orden.getSort().toList()).containsExactly(Sort.Order.desc("id"));
    }

    @Test
    void listarConservaElOrdenPedidoYAgregaIdComoDesempate() {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        service.listar(null, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "asunto")));

        Pageable orden = capturarOrdenDeListado();
        assertThat(orden.getSort().stream().map(Sort.Order::getProperty)).containsExactly("asunto", "id");
        assertThat(orden.getSort().getOrderFor("asunto").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(orden.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listarAceptaLasClavesDeOrdenDeLaUi() {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        assertThatCode(() -> {
            for (String propiedad : List.of("fechaCreacion", "nombreRemitente", "correoRemitente",
                    "asunto", "correoEnviado", "atendido")) {
                service.listar(null, PageRequest.of(0, 10, Sort.by(propiedad)));
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void listarRechazaPropiedadesDeOrdenNoPermitidas() {
        assertThatThrownBy(() -> service.listar(null, PageRequest.of(0, 10, Sort.by("fechaEnvio"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fechaEnvio")
                .hasMessageContaining("Valores permitidos");
        verify(repository, never()).findAll(any(Pageable.class));
    }

    private Pageable capturarOrdenDeListado() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(captor.capture());
        return captor.getValue();
    }
}
