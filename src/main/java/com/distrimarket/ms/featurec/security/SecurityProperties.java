package com.distrimarket.ms.featurec.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Seguridad de ventas (prefijo {@code security} en application.yml): clave y emisor del JWT
 * (los de distrimarket-ms-auth), rutas públicas y roles por ruta.
 */
@ConfigurationProperties(prefix = "security")
public record SecurityProperties(Jwt jwt, List<String> publicPaths, List<UrlRule> rules) {

    public SecurityProperties {
        if (jwt == null) {
            throw new IllegalStateException("Falta la configuración security.jwt.");
        }
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public record Jwt(String secret, String issuer) {
        public Jwt {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException("security.jwt.secret debe tener al menos 32 caracteres.");
            }
            if (issuer == null || issuer.isBlank()) {
                throw new IllegalStateException("security.jwt.issuer es obligatorio.");
            }
        }
    }

    /** Regla por URL (relativa a /ventas): si {@code methods} está vacío aplica a todos. */
    public record UrlRule(String pattern, List<String> methods, List<String> roles) {
        public UrlRule {
            methods = methods == null ? List.of() : List.copyOf(methods);
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }
}
