package com.minicalendly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MiniCalendlyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniCalendlyApplication.class, args);
    }
}
