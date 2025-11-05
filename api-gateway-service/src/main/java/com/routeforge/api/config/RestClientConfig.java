package com.routeforge.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * REST client configuration for inter-service communication
 */
@Configuration
public class RestClientConfig {
    
    @Value("${routeforge.processing-service.url:http://localhost:8084}")
    private String processingServiceUrl;
    
    @Bean
    public RestClient processingServiceClient() {
        return RestClient.builder()
            .baseUrl(processingServiceUrl)
            .build();
    }
}
