package com.bluecollar.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        Map<String, Object> extensions = new HashMap<>();
        extensions.put("x-roles", Arrays.asList("ADMIN", "CUSTOMER", "WORKER"));

        return new OpenAPI()
                .info(new Info()
                        .title("BlueCollar API")
                        .description("BlueCollar REST API for connecting workers and customers. " +
                                "JWT tokens encode user role (ADMIN, CUSTOMER, WORKER). " +
                                "Admin endpoints require ADMIN role in token.")
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Bearer token. Token must contain role claim (sub, roles, or custom claim). " +
                                        "Admin endpoints require role=ADMIN. " +
                                        "Customer endpoints require role=CUSTOMER. " +
                                        "Worker endpoints require role=WORKER.")
                                .extensions(extensions)));
    }
}
