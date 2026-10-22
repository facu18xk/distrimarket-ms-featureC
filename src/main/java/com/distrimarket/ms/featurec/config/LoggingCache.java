package com.distrimarket.ms.featurec.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Decora una caché para dejar en el log cada operación (GET con HIT/MISS, SET, DELETE)
 * con la clave real de Redis. Es la trazabilidad pedida y la evidencia de que la caché se usa.
 */
public class LoggingCache implements Cache {

    private static final Logger log = LoggerFactory.getLogger(LoggingCache.class);

    private final Cache delegate;
    private final CacheConfig.Properties properties;
    private final Duration ttl;

    public LoggingCache(Cache delegate, CacheConfig.Properties properties) {
        this.delegate = delegate;
        this.properties = properties;
        this.ttl = properties.ttl(delegate.getName());
    }

    /** Entrega cada caché del CacheManager real envuelta en {@link LoggingCache}. */
    public static class Manager implements CacheManager {

        private final CacheManager delegate;
        private final CacheConfig.Properties properties;
        private final Map<String, Cache> caches = new ConcurrentHashMap<>();

        public Manager(CacheManager delegate, CacheConfig.Properties properties) {
            this.delegate = delegate;
            this.properties = properties;
        }

        @Override
        public Cache getCache(String name) {
            Cache cache = caches.get(name);
            if (cache != null) {
                return cache;
            }
            Cache target = delegate.getCache(name);
            return target == null ? null
                    : caches.computeIfAbsent(name, ignored -> new LoggingCache(target, properties));
        }

        @Override
        public Collection<String> getCacheNames() {
            return delegate.getCacheNames();
        }
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper value = delegate.get(key);
        logGet(key, value != null);
        return value;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        T value = delegate.get(key, type);
        logGet(key, value != null);
        return value;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper cached = delegate.get(key);
        if (cached != null) {
            logGet(key, true);
            @SuppressWarnings("unchecked")
            T value = (T) cached.get();
            return value;
        }
        logGet(key, false);
        T value = delegate.get(key, valueLoader);
        logSet(key);
        return value;
    }

    @Override
    public CompletableFuture<?> retrieve(Object key) {
        CompletableFuture<?> future = delegate.retrieve(key);
        return future == null ? null : future.whenComplete((value, error) -> logGet(key, value != null));
    }

    @Override
    public <T> CompletableFuture<T> retrieve(Object key, Supplier<CompletableFuture<T>> valueLoader) {
        return delegate.retrieve(key, valueLoader);
    }

    @Override
    public void put(Object key, Object value) {
        delegate.put(key, value);
        logSet(key);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        ValueWrapper previous = delegate.putIfAbsent(key, value);
        if (previous == null) {
            logSet(key);
        }
        return previous;
    }

    @Override
    public void evict(Object key) {
        delegate.evict(key);
        log.info("Caché DELETE {}", properties.redisKey(getName(), key));
    }

    @Override
    public boolean evictIfPresent(Object key) {
        boolean removed = delegate.evictIfPresent(key);
        log.info("Caché DELETE {} ({})", properties.redisKey(getName(), key), removed ? "existía" : "no existía");
        return removed;
    }

    @Override
    public void clear() {
        delegate.clear();
        log.info("Caché DELETE {}* (sección '{}' completa)", properties.redisKey(getName(), ""), getName());
    }

    @Override
    public boolean invalidate() {
        boolean removed = delegate.invalidate();
        log.info("Caché DELETE {}* (sección '{}' completa)", properties.redisKey(getName(), ""), getName());
        return removed;
    }

    private void logGet(Object key, boolean hit) {
        log.info("Caché GET {} -> {}", properties.redisKey(getName(), key), hit ? "HIT" : "MISS");
    }

    private void logSet(Object key) {
        log.info("Caché SET {} ({})", properties.redisKey(getName(), key), CacheConfig.Properties.describe(ttl));
    }
}
