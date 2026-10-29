package com.distrimarket.ms.featurec.job;

import com.distrimarket.commons.entity.ContactoMensaje;
import com.distrimarket.ms.featurec.service.CorreoService;
import com.distrimarket.ms.featurec.service.SoporteService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoporteCorreoJobTest {

    private static final String SOPORTE = "soporte.distrimarket@gmail.com";

    private final SoporteService soporte = mock(SoporteService.class);
    private final CorreoService correo = mock(CorreoService.class);
    private final SoporteCorreoJob job = new SoporteCorreoJob(soporte, correo, 20, 5);

    private static ContactoMensaje mensaje(long id) {
        ContactoMensaje m = ContactoMensaje.builder()
                .nombreRemitente("Ana").correoRemitente("ana@example.test").asunto("Ayuda").mensaje("Hola").build();
        m.setId(id);
        return m;
    }

    @Test
    void reenviaASoporteConResponderAlRemitente() {
        when(correo.correoSoporte()).thenReturn(SOPORTE);
        when(soporte.pendientesDeCorreo(20, 5)).thenReturn(List.of(mensaje(1)));

        job.enviarPendientes();

        verify(correo).enviar(eq(SOPORTE), startsWith("[Soporte DistriMarket] Ayuda"), anyString(), eq("ana@example.test"));
        verify(soporte).registrarResultadoCorreo(1L, null);
    }

    @Test
    void siFallaGuardaElErrorParaReintentar() {
        when(correo.correoSoporte()).thenReturn(SOPORTE);
        when(soporte.pendientesDeCorreo(20, 5)).thenReturn(List.of(mensaje(2)));
        doThrow(new IllegalStateException("SMTP caído")).when(correo).enviar(any(), any(), any(), any());

        job.enviarPendientes();

        verify(soporte).registrarResultadoCorreo(2L, "SMTP caído");
    }

    @Test
    void sinPendientesNoHaceNada() {
        when(soporte.pendientesDeCorreo(20, 5)).thenReturn(List.of());

        job.enviarPendientes();

        verify(correo, never()).enviar(any(), any(), any(), any());
    }
}
