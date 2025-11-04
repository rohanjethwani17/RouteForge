package com.routeforge.api.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Admin Client configuration for DLQ monitoring
 */
@Configuration
@ConfigurationProperties(prefix = "routeforge.kafka")
@Getter
@Setter
public class KafkaAdminConfig {
    
    private String bootstrapServers;
    private String dlqTopic;
    
    @Bean
    public AdminClient kafkaAdminClient() {
        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        return AdminClient.create(config);
    }
}
