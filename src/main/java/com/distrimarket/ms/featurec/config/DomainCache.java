package com.distrimarket.ms.featurec.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.function.Function;

/**
 * Uso programático del {@link CacheManager}, para lo que las anotaciones no cubren.
 * Caso típico: un listado paginado no se cachea entero, pero cada elemento sí, por su id.
 * Un fallo de Redis se registra y no interrumpe la operación.
 */
@Slf4j
@Component
public class DomainCache {

    private final CacheManager cacheManager;

    public DomainCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public <T> void putAll(String domain, Collection<T> items, Function<T, ?> keyOf) {
        Cache cache = cache(domain);
        try {
            for (T item : items) {
                Object key = keyOf.apply(item);
                if (key != null) {
                    cache.put(key, item);
                }
            }
        } catch (RuntimeException ex) {
            log.warn("No se pudieron guardar en caché los elementos de '{}': {}", domain, ex.getMessage());
        }
    }

    public void put(String domain, Object key, Object value) {
        if (key == null || value == null) {
            return;
        }
        try {
            cache(domain).put(key, value);
        } catch (RuntimeException ex) {
            log.warn("No se pudo guardar en caché {}::{}: {}", domain, key, ex.getMessage());
        }
    }

    public void evict(String domain, Object key) {
        try {
            cache(domain).evict(key);
        } catch (RuntimeException ex) {
            log.error("No se pudo quitar de caché {}::{}: {}", domain, key, ex.getMessage());
        }
    }

    private Cache cache(String domain) {
        Cache cache = cacheManager.getCache(domain);
        if (cache == null) {
            throw new IllegalStateException("No existe la sección de caché '" + domain + "' en distrimarket.cache.domains (CacheConfig).");
        }
        return cache;
    }
}
