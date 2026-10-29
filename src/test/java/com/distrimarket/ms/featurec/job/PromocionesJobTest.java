package com.distrimarket.ms.featurec.job;

import com.distrimarket.commons.entity.CampaniaEnvio;
import com.distrimarket.commons.entity.CampaniaPromocion;
import com.distrimarket.ms.featurec.repository.CampaniaEnvioRepository;
import com.distrimarket.ms.featurec.repository.CampaniaPromocionRepository;
import com.distrimarket.ms.featurec.repository.CampaniaPromocionRepository.Destinatario;
import com.distrimarket.ms.featurec.service.CorreoService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromocionesJobTest {

    private final CampaniaPromocionRepository promociones = mock(CampaniaPromocionRepository.class);
    private final CampaniaEnvioRepository envios = mock(CampaniaEnvioRepository.class);
    private final CorreoService correo = mock(CorreoService.class);
    private final CacheManager cache = new ConcurrentMapCacheManager("promocion");

    private static CampaniaPromocion promocion() {
        CampaniaPromocion p = new CampaniaPromocion();
        p.setId(7L);
        p.setTitulo("Promo leche");
        p.setCuerpoHtml("10% en lácteos");
        p.setIdsProducto(Set.of(1L));
        return p;
    }

    record Cliente(Long getIdCliente, String getCorreo, String getNombre) implements Destinatario {
    }

    private static Destinatario cliente(long id, String correo) {
        return new Cliente(id, correo, "Cliente " + id);
    }

    private PromocionesJob job(int lote) {
        when(correo.correoSoporte()).thenReturn("soporte.distrimarket@gmail.com");
        return new PromocionesJob(promociones, envios, correo, cache, lote);
    }

    @Test
    void enviaACadaClienteRegistraElEnvioYTerminaLaPromocion() {
        CampaniaPromocion p = promocion();
        when(promociones.findListasParaEnviar(any())).thenReturn(List.of(p));
        when(promociones.findDestinatariosPendientes(eq(7L), any(), any(), any()))
                .thenReturn(List.of(cliente(1, "a@example.test"), cliente(2, "b@example.test")));

        job(50).enviarPromociones();

        verify(correo).enviar(eq("a@example.test"), eq("Promo leche"), anyString(), any());
        verify(correo).enviar(eq("b@example.test"), eq("Promo leche"), anyString(), any());
        ArgumentCaptor<CampaniaEnvio> envio = ArgumentCaptor.forClass(CampaniaEnvio.class);
        verify(envios, times(2)).save(envio.capture());
        assertThat(envio.getAllValues()).allMatch(e -> e.getEnviado() && e.getIdCampania() == 7L);
        assertThat(p.getEnviado()).isTrue();
        verify(promociones).save(p);
    }

    @Test
    void unEnvioFallidoQuedaRegistradoConSuError() {
        when(promociones.findListasParaEnviar(any())).thenReturn(List.of(promocion()));
        when(promociones.findDestinatariosPendientes(eq(7L), any(), any(), any()))
                .thenReturn(List.of(cliente(1, "a@example.test")));
        doThrow(new IllegalStateException("SMTP rechazó")).when(correo).enviar(eq("a@example.test"), any(), any(), any());

        job(50).enviarPromociones();

        ArgumentCaptor<CampaniaEnvio> envio = ArgumentCaptor.forClass(CampaniaEnvio.class);
        verify(envios).save(envio.capture());
        assertThat(envio.getValue().getEnviado()).isFalse();
        assertThat(envio.getValue().getError()).isEqualTo("SMTP rechazó");
    }

    @Test
    void conElLoteLlenoLaPromocionSigueParaLaProximaEjecucion() {
        CampaniaPromocion p = promocion();
        when(promociones.findListasParaEnviar(any())).thenReturn(List.of(p));
        when(promociones.findDestinatariosPendientes(eq(7L), any(), any(), any()))
                .thenReturn(List.of(cliente(1, "a@example.test"), cliente(2, "b@example.test")));

        job(2).enviarPromociones();

        assertThat(p.getEnviado()).isFalse();
        verify(promociones, never()).save(p);
    }
}
