package com.distrimarket.ms.featurec.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

// Sin @Configuration a propósito: solo aplica al cliente de inventario que la referencia.
public class InventarioClientConfiguration {

    @Bean
    public ErrorDecoder inventarioErrorDecoder() {
        return new InventarioErrorDecoder();
    }

    @Bean
    public RequestInterceptor tokenPropagationInterceptor() {
        return new TokenPropagationInterceptor();
    }
}
