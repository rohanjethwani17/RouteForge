package com.routeforge.ingestion.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "routeforge.ingestion")
public class IngestionProperties {
    
    private String feedUrl;
    private int pollIntervalSec = 5;
    private int timeoutSec = 10;
    private String topic = "vehicle_positions";
    private boolean enabled = true;
}
