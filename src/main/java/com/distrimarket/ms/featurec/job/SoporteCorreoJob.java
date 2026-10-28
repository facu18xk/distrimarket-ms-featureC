package com.distrimarket.ms.featurec.job;

import com.distrimarket.commons.entity.ContactoMensaje;
import com.distrimarket.ms.featurec.service.CorreoService;
import com.distrimarket.ms.featurec.service.SoporteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Job programado: reenvía al correo de soporte los mensajes del formulario de contacto.
 * Corre cada {@code distrimarket.jobs.soporte.intervalo} ms; un mensaje que falla se reintenta
 * hasta {@code max-intentos} veces y el error queda guardado en el mensaje.
 */
@Slf4j
@Component
public class SoporteCorreoJob {

    private final SoporteService soporteService;
    private final CorreoService correoService;
    private final int lote;
    private final int maxIntentos;

    public SoporteCorreoJob(SoporteService soporteService,
                            CorreoService correoService,
                            @Value("${distrimarket.jobs.soporte.lote:20}") int lote,
                            @Value("${distrimarket.jobs.soporte.max-intentos:5}") int maxIntentos) {
        this.soporteService = soporteService;
        this.correoService = correoService;
        this.lote = lote;
        this.maxIntentos = maxIntentos;
    }

    @Scheduled(initialDelayString = "${distrimarket.jobs.soporte.demora-inicial:30000}",
            fixedDelayString = "${distrimarket.jobs.soporte.intervalo:60000}")
    public void enviarPendientes() {
        List<ContactoMensaje> pendientes = soporteService.pendientesDeCorreo(lote, maxIntentos);
        if (pendientes.isEmpty()) {
            return;
        }
        log.info("Job soporte: {} mensaje(s) para reenviar a {}", pendientes.size(), correoService.correoSoporte());
        for (ContactoMensaje m : pendientes) {
            try {
                correoService.enviar(
                        correoService.correoSoporte(),
                        "[Soporte DistriMarket] " + m.getAsunto(),
                        "Mensaje #" + m.getId() + " de " + m.getNombreRemitente() + " <" + m.getCorreoRemitente() + ">\n"
                                + "Recibido: " + m.getFechaEnvio() + "\n\n" + m.getMensaje(),
                        m.getCorreoRemitente());
                soporteService.registrarResultadoCorreo(m.getId(), null);
            } catch (RuntimeException ex) {
                log.warn("Job soporte: no se pudo enviar el mensaje {}: {}", m.getId(), ex.getMessage());
                soporteService.registrarResultadoCorreo(m.getId(), ex.getMessage());
            }
        }
    }
}
