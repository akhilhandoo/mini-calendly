package com.minicalendly.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI miniCalendlyOpenApi() {
        return new OpenAPI().info(new Info()
                .title("mini-calendly API")
                .version("v1")
                .description("""
                        Manage users' availability slots, convert free slots into meetings and query \
                        aggregated free/busy views. All timestamps are ISO-8601; responses are in UTC."""));
    }
}
