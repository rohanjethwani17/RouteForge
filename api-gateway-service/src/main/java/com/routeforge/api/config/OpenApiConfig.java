package com.routeforge.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI routeForgeOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("RouteForge API")
                .description("Real-time public transit tracking platform API")
                .version("v0.1.0")
                .contact(new Contact()
                    .name("RouteForge Team")
                    .url("https://github.com/routeforge")
                    .email("support@routeforge.io"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
            .components(new Components()
                .addSecuritySchemes("bearer-jwt",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT token for admin endpoints")))
            .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
