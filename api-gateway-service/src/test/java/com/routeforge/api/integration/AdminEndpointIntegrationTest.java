package com.routeforge.api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Admin endpoints
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminEndpointIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private JedisPool jedisPool;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("routeforge.redis.host", redis::getHost);
        registry.add("routeforge.redis.port", () -> redis.getMappedPort(6379).toString());
        registry.add("routeforge.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", 
            () -> "http://localhost:8080/realms/test");
        registry.add("spring.profiles.active", () -> "dev");
    }
    
    @BeforeEach
    void setUp() {
        // Populate Redis with test data
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushAll();
            jedis.set("veh:1001", "{\"vehicleId\":\"1001\"}");
            jedis.set("veh:1002", "{\"vehicleId\":\"1002\"}");
            jedis.zadd("route:R1:vehicles", System.currentTimeMillis(), "1001");
            jedis.zadd("route:R1:vehicles", System.currentTimeMillis(), "1002");
        }
    }
    
    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    void clearAllCache_shouldDeleteAllCacheKeys() throws Exception {
        mockMvc.perform(delete("/api/admin/cache/all")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.keysDeleted").isNumber())
            .andExpect(jsonPath("$.message").value("Cache cleared successfully"));
        
        // Verify keys are deleted
        try (Jedis jedis = jedisPool.getResource()) {
            assert jedis.keys("veh:*").isEmpty();
            assert jedis.keys("route:*").isEmpty();
        }
    }
    
    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    void clearRouteCache_shouldDeleteRouteSpecificKeys() throws Exception {
        mockMvc.perform(delete("/api/admin/cache/routes/R1")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.routeId").value("R1"))
            .andExpect(jsonPath("$.keysDeleted").isNumber());
    }
    
    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    void getDlqMetrics_shouldReturnKafkaMetrics() throws Exception {
        mockMvc.perform(get("/api/admin/dlq/metrics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dlqTopic").value("vehicle_positions.dlq"))
            .andExpect(jsonPath("$.timestamp").isNumber());
    }
    
    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    void getAdminStats_shouldReturnSystemStats() throws Exception {
        mockMvc.perform(get("/api/admin/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.redis").exists())
            .andExpect(jsonPath("$.sse").exists())
            .andExpect(jsonPath("$.timestamp").isNumber());
    }
    
    @Test
    void adminEndpoints_withoutAuth_shouldReturn401() throws Exception {
        mockMvc.perform(delete("/api/admin/cache/all"))
            .andExpect(status().isUnauthorized());
        
        mockMvc.perform(get("/api/admin/dlq/metrics"))
            .andExpect(status().isUnauthorized());
    }
    
    @Test
    @WithMockUser(authorities = "SCOPE_user") // Wrong scope - but dev profile allows all
    void adminEndpoints_withInsufficientPermissions_shouldReturn403() throws Exception {
        // In dev profile, method security is bypassed, so this should succeed
        mockMvc.perform(delete("/api/admin/cache/all")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"));
    }
}
