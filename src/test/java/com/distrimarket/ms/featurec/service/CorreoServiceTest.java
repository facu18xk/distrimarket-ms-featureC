package com.distrimarket.ms.featurec.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorreoServiceTest {

    private static final String SOPORTE = "soporte.distrimarket@gmail.com";

    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> proveedor = mock(ObjectProvider.class);
    private final JavaMailSender sender = mock(JavaMailSender.class);

    @Test
    void deshabilitadoNoEnviaNada() {
        when(proveedor.getIfAvailable()).thenReturn(sender);
        CorreoService correo = new CorreoService(proveedor, false, SOPORTE, SOPORTE);

        correo.enviar("cliente@example.test", "Asunto", "Texto", null);

        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void habilitadoEnviaConRemitenteYResponderA() {
        when(proveedor.getIfAvailable()).thenReturn(sender);
        CorreoService correo = new CorreoService(proveedor, true, SOPORTE, SOPORTE);

        correo.enviar(SOPORTE, "[Soporte] Ayuda", "Texto", "cliente@example.test");

        ArgumentCaptor<SimpleMailMessage> mensaje = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(mensaje.capture());
        assertThat(mensaje.getValue().getFrom()).isEqualTo(SOPORTE);
        assertThat(mensaje.getValue().getTo()).containsExactly(SOPORTE);
        assertThat(mensaje.getValue().getReplyTo()).isEqualTo("cliente@example.test");
        assertThat(mensaje.getValue().getSubject()).isEqualTo("[Soporte] Ayuda");
    }

    @Test
    void habilitadoSinServidorConfiguradoFalla() {
        when(proveedor.getIfAvailable()).thenReturn(null);
        CorreoService correo = new CorreoService(proveedor, true, SOPORTE, SOPORTE);

        assertThatThrownBy(() -> correo.enviar("x@example.test", "A", "T", null))
                .isInstanceOf(IllegalStateException.class);
    }
}
