package com.distrimarket.ms.featurec.config;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "pagination")
@Validated
public class PaginationProperties {
    @Min(0)
    private int defaultPage = 0;
    @Min(1)
    private int defaultPageSize = 10;
    @Min(1)
    private int maxPageSize = 100;
    private boolean oneIndexedParameters;
}