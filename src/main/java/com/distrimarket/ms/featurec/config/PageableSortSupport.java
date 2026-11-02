package com.distrimarket.ms.featurec.config;

import com.distrimarket.commons.service.Paginas;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Arrays;

/**
 * Orden seguro para los listados paginados. Delega en {@link Paginas#conOrdenPermitido}:
 * rechaza propiedades no admitidas con {@link IllegalArgumentException}, usa "id" DESC cuando
 * el cliente no pidió orden y agrega "id" como último criterio de desempate.
 */
public final class PageableSortSupport {
    private static final Sort ORDEN_POR_DEFECTO = Sort.by(Sort.Direction.DESC, "id");

    private PageableSortSupport() {
    }

    public static Pageable allowSorts(Pageable pageable, String... allowedProperties) {
        return Paginas.conOrdenPermitido(pageable, Arrays.asList(allowedProperties), ORDEN_POR_DEFECTO);
    }
}
