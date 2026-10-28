package com.distrimarket.ms.featurec.job;

import com.distrimarket.commons.entity.CampaniaEnvio;
import com.distrimarket.commons.entity.CampaniaPromocion;
import com.distrimarket.ms.featurec.config.CacheConfig;
import com.distrimarket.ms.featurec.repository.CampaniaEnvioRepository;
import com.distrimarket.ms.featurec.repository.CampaniaPromocionRepository;
import com.distrimarket.ms.featurec.repository.CampaniaPromocionRepository.Destinatario;
import com.distrimarket.ms.featurec.service.CorreoService;
import com.distrimarket.ms.featurec.service.PromocionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Job programado: envía cada promoción cuya fecha de envío llegó a los clientes que compraron
 * alguno de sus productos (en los últimos {@code diasHistorial} días). Cada envío queda en
 * campania_envios, así nadie la recibe dos veces y el job puede retomar si se corta. Cuando ya no
 * quedan clientes pendientes, la promoción se marca como enviada.
 */
@Slf4j
@Component
public class PromocionesJob {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CampaniaPromocionRepository promociones;
    private final CampaniaEnvioRepository envios;
    private final CorreoService correoService;
    private final CacheManager cacheManager;
    private final int lote;

    public PromocionesJob(CampaniaPromocionRepository promociones,
                          CampaniaEnvioRepository envios,
                          CorreoService correoService,
                          CacheManager cacheManager,
                          @Value("${distrimarket.jobs.promociones.lote:50}") int lote) {
        this.promociones = promociones;
        this.envios = envios;
        this.correoService = correoService;
        this.cacheManager = cacheManager;
        this.lote = lote;
    }

    @Scheduled(initialDelayString = "${distrimarket.jobs.promociones.demora-inicial:60000}",
            fixedDelayString = "${distrimarket.jobs.promociones.intervalo:300000}")
    public void enviarPromociones() {
        for (CampaniaPromocion promocion : promociones.findListasParaEnviar(LocalDateTime.now())) {
            enviar(promocion);
        }
    }

    private void enviar(CampaniaPromocion promocion) {
        List<Destinatario> pendientes = promociones.findDestinatariosPendientes(
                promocion.getId(), promocion.getIdsProducto(), PromocionService.desde(promocion), PageRequest.of(0, lote));
        log.info("Job promociones: '{}' ({}) → {} cliente(s) en este lote", promocion.getTitulo(), promocion.getId(), pendientes.size());

        for (Destinatario d : pendientes) {
            CampaniaEnvio envio = CampaniaEnvio.builder()
                    .idCampania(promocion.getId())
                    .idCliente(d.getIdCliente())
                    .correo(d.getCorreo())
                    .build();
            try {
                correoService.enviar(d.getCorreo(), promocion.getTitulo(), texto(promocion, d.getNombre()), correoService.correoSoporte());
                envio.setEnviado(true);
            } catch (RuntimeException ex) {
                log.warn("Job promociones: falló el envío a {}: {}", d.getCorreo(), ex.getMessage());
                envio.setError(ex.getMessage() == null ? "Error desconocido" : ex.getMessage().substring(0, Math.min(500, ex.getMessage().length())));
            }
            envios.save(envio);
        }

        // Si el lote vino incompleto, ya no quedan clientes: la promoción terminó de enviarse.
        if (pendientes.size() < lote) {
            promocion.setEnviado(true);
            promociones.save(promocion);
            log.info("Job promociones: '{}' ({}) enviada", promocion.getTitulo(), promocion.getId());
        }
        // La promoción en caché cambió (correos enviados, estado): se borra para que se vuelva a leer.
        Cache cache = cacheManager.getCache(CacheConfig.PROMOCION);
        if (cache != null) {
            cache.evict(promocion.getId());
        }
    }

    private String texto(CampaniaPromocion p, String nombre) {
        StringBuilder sb = new StringBuilder("Hola ").append(nombre).append(",\n\n").append(p.getCuerpoHtml()).append("\n");
        if (p.getPorcentajeDescuento() != null) {
            sb.append("\nDescuento: ").append(p.getPorcentajeDescuento().setScale(0, RoundingMode.HALF_UP)).append("%");
        }
        if (p.getFechaInicio() != null || p.getFechaFin() != null) {
            sb.append("\nVigencia: ")
                    .append(p.getFechaInicio() == null ? "desde hoy" : "del " + p.getFechaInicio().format(FECHA))
                    .append(p.getFechaFin() == null ? "" : " al " + p.getFechaFin().format(FECHA));
        }
        return sb.append("\n\nTe escribimos porque compraste estos productos en DistriMarket.\n").toString();
    }
}
