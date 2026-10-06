package com.distrimarket.ms.featurec.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
public class PaginationConfiguration {

    @Bean
    PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer(PaginationProperties properties) {
        return resolver -> {
            resolver.setFallbackPageable(PageRequest.of(
                    properties.getDefaultPage(),
                    properties.getDefaultPageSize(),
                    Sort.by(Sort.Direction.DESC, "id")));
            resolver.setMaxPageSize(properties.getMaxPageSize());
        };
    }
}
