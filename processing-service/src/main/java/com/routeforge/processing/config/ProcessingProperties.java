package com.routeforge.processing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "routeforge.processing")
public class ProcessingProperties {
    
    private String topic = "vehicle_positions";
    private String dlqTopic = "vehicle_positions.dlq";
    private int batchSize = 50;
    private int batchTimeoutMs = 5000;
    private String kafkaBootstrapServers = "localhost:9092";
}
