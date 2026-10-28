package com.distrimarket.ms.featurec.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa los jobs programados (@Scheduled): reenvío de mensajes de soporte y envío de promociones.
 * Con {@code distrimarket.jobs.enabled=false} no corre ninguno (por ejemplo, en una segunda
 * instancia de ventas detrás del balanceador, para no enviar dos veces).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "distrimarket.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
