package com.distrimarket.ms.featurec.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${openapi.server-url:${server.servlet.context-path:/api/v1}}")
    private String serverUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .servers(List.of(new Server().url(serverUrl).description("Servidor configurado")))
                .info(new Info()
                        .title("Distrimarket MS Feature C - Facturación y Ventas")
                        .version("1.0.0")
                        .description("Microservicio encargado de clientes, timbrados y facturación. La factura vincula a cliente y depósito como entidad asociativa: cada cliente puede comprar en varios depósitos y cada depósito vender a varios clientes.")
                        .contact(new Contact().name("Equipo Distrimarket").email("soporte@distrimarket.com"))
                        .license(new License().name("Apache 2.0").url("https://springdoc.org")));
    }
}