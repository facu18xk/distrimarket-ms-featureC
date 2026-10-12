package com.distrimarket.ms.featurec.config;

import com.distrimarket.ms.featurec.logging.RequestLoggingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final RequestLoggingInterceptor requestLoggingInterceptor;

    public WebConfiguration(RequestLoggingInterceptor requestLoggingInterceptor) {
        this.requestLoggingInterceptor = requestLoggingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLoggingInterceptor);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Contrato centralizado que viene dentro del jar distrimarket-common
        registry.addResourceHandler("/openapi.yaml")
                .addResourceLocations("classpath:/static/openapi.yaml", "classpath:/openapi.yaml");
    }
}
