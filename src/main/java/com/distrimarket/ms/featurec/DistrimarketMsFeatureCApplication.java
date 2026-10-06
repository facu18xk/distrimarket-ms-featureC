package com.distrimarket.ms.featurec;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan("com.distrimarket.commons.entity")
public class DistrimarketMsFeatureCApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistrimarketMsFeatureCApplication.class, args);
    }
}
