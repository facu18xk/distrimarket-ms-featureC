package com.distrimarket.ms.featurec;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

public class ServletInitializer extends SpringBootServletInitializer {
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder app) {
        return app.sources(DistrimarketMsFeatureCApplication.class);
    }
}
