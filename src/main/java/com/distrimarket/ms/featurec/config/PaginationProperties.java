package com.distrimarket.ms.featurec.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "pagination")
public class PaginationProperties {
    private int defaultPage = 0;
    private int defaultPageSize = 10;
    private int maxPageSize = 100;
}