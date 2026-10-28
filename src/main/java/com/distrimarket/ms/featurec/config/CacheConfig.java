package com.distrimarket.ms.featurec.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caché de ventas en el Redis compartido, organizada por dominios. Cada dominio tiene su TTL en
 * application.yml ({@code distrimarket.cache.domains}) y se invalida sin tocar a los demás.
 * Claves en Redis: {@code distrimarket:ventas:<dominio>:<id>}, por ejemplo {@code distrimarket:ventas:cliente:15}.
 *
 * <p>La caché se aplica por fuera de la transacción ({@code order = HIGHEST_PRECEDENCE}):
 * un {@code @CachePut} escribe recién cuando la base ya confirmó el cambio.</p>
 */
@Slf4j
@Configuration
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
@EnableConfigurationProperties(CacheConfig.Properties.class)
public class CacheConfig implements CachingConfigurer {

    // ---------------- Dominios (secciones) de la caché ----------------

    /** ClienteResponseDTO por id. */
    public static final String CLIENTE = "cliente";
    /** MedioPagoDTO por id. Catálogo estable: persistente. */
    public static final String MEDIO_PAGO = "medio-pago";
    /** TimbradoDTO por id. Catálogo estable: persistente. */
    public static final String TIMBRADO = "timbrado";
    /**
     * FacturaVentaResponseDTO por id (con sus líneas). Muestra el nombre del cliente, del medio
     * de pago y el timbrado: se invalida completa cuando cambia alguno de ellos.
     */
    public static final String FACTURA_VENTA = "factura-venta";
    /** PromocionResponseDTO por id (con sus productos y cuántos correos se enviaron). */
    public static final String PROMOCION = "promocion";

    /**
     * Parámetros de application.yml (prefijo {@code distrimarket.cache}).
     *
     * @param enabled       si es false no se guarda nada y todo va a la base
     * @param keyPrefix     inicio de cada clave en Redis, por ejemplo {@code distrimarket:ventas}
     * @param defaultTtl    vida por defecto de una entrada
     * @param scanBatchSize claves por lote al invalidar una sección completa (SCAN, no KEYS)
     * @param domains       TTL de cada dominio; {@code 0} = persistente (no vence)
     */
    @ConfigurationProperties(prefix = "distrimarket.cache")
    public record Properties(boolean enabled, String keyPrefix, Duration defaultTtl, int scanBatchSize,
                             Map<String, Duration> domains) {

        public Properties {
            if (keyPrefix == null || keyPrefix.isBlank()) {
                throw new IllegalStateException("distrimarket.cache.key-prefix es obligatorio.");
            }
            defaultTtl = defaultTtl == null ? Duration.ZERO : defaultTtl;
            scanBatchSize = scanBatchSize <= 0 ? 100 : scanBatchSize;
            domains = domains == null ? Map.of() : Map.copyOf(domains);
        }

        /** Clave completa en Redis: {@code <prefijo>:<dominio>:<id>}. */
        public String redisKey(String domain, Object key) {
            return keyPrefix + ":" + domain + ":" + key;
        }

        public Duration ttl(String domain) {
            return domains.getOrDefault(domain, defaultTtl);
        }

        public static String describe(Duration ttl) {
            return ttl == null || ttl.isZero() ? "persistente" : "TTL " + ttl;
        }
    }

    @Bean
    public CacheManager cacheManager(Properties properties, ObjectProvider<RedisConnectionFactory> connectionFactory) {
        if (!properties.enabled()) {
            log.warn("Caché deshabilitada (distrimarket.cache.enabled=false): todas las lecturas van a la base");
            return new LoggingCache.Manager(new NoOpCacheManager(), properties);
        }
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .computePrefixWith(domain -> properties.keyPrefix() + ":" + domain + ":")
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(valueSerializer()));

        Map<String, RedisCacheConfiguration> domains = new LinkedHashMap<>();
        properties.domains().forEach((domain, ttl) -> domains.put(domain, base.entryTtl(ttl)));

        RedisCacheManager redis = RedisCacheManager
                .builder(RedisCacheWriter.nonLockingRedisCacheWriter(
                        connectionFactory.getObject(), BatchStrategies.scan(properties.scanBatchSize())))
                .cacheDefaults(base.entryTtl(properties.defaultTtl()))
                .withInitialCacheConfigurations(domains)
                // Solo existen los dominios declarados: un nombre mal escrito falla en vez de crear otra sección.
                .disableCreateOnMissingCache()
                .build();
        redis.initializeCaches();
        properties.domains().forEach((domain, ttl) -> log.info("Caché '{}' -> claves {}<id> ({})",
                domain, properties.redisKey(domain, ""), Properties.describe(ttl)));
        return new LoggingCache.Manager(redis, properties);
    }

    /** Si Redis no responde, la operación sigue contra la base: la caché acelera, no es obligatoria. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Caché no disponible en GET {}::{}; se consulta la base de datos ({})",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Caché no disponible en SET {}::{} ({})", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.error("Caché no disponible en DELETE {}::{}; puede quedar desactualizada hasta su TTL ({})",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.error("Caché no disponible al limpiar la sección {}; puede quedar desactualizada hasta su TTL ({})",
                        cache.getName(), exception.getMessage());
            }
        };
    }

    /**
     * JSON con el tipo de cada objeto (para reconstruir el DTO al leer) y fechas ISO.
     * Al leer solo se aceptan clases del proyecto y de java.*.
     */
    public static RedisSerializer<Object> valueSerializer() {
        BasicPolymorphicTypeValidator allowed = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.distrimarket.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.lang.")
                .build();
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .activateDefaultTyping(allowed, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY)
                .build();
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
