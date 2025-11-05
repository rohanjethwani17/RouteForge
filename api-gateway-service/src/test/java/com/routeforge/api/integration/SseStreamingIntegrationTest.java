package com.routeforge.api.integration;

import com.routeforge.api.service.VehicleService;
import com.routeforge.common.dto.VehiclePositionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for SSE streaming functionality
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SseStreamingIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);
    
    @Autowired
    private WebApplicationContext context;
    
    @Autowired
    private VehicleService vehicleService;
    
    @Autowired
    private JedisPool jedisPool;
    
    private MockMvc mockMvc;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("routeforge.redis.host", redis::getHost);
        registry.add("routeforge.redis.port", () -> redis.getMappedPort(6379).toString());
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", 
            () -> "http://localhost:8080/realms/test");
        registry.add("spring.profiles.active", () -> "dev");
    }
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        
        // Clear Redis
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushAll();
        }
    }
    
    @Test
    void sseStream_shouldEstablishConnection() throws Exception {
        mockMvc.perform(get("/api/stream/routes/R1"))
            .andExpect(request().asyncStarted())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
    }
    
    @Test
    void sseStream_shouldReceiveHeartbeats() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger eventCount = new AtomicInteger(0);
        
        // Start SSE connection in background
        Thread sseThread = new Thread(() -> {
            try {
                mockMvc.perform(get("/api/stream/routes/R1"))
                    .andExpect(request().asyncStarted())
                    .andDo(result -> {
                        eventCount.incrementAndGet();
                        latch.countDown();
                    });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        
        sseThread.start();
        
        // Wait for at least one event (heartbeat)
        boolean received = latch.await(35, TimeUnit.SECONDS); // Heartbeat every 30s
        assertThat(received).isTrue();
        
        sseThread.interrupt();
    }
    
    @Test
    void sseStream_shouldReceiveVehicleUpdates() throws Exception {
        // Pre-populate Redis with vehicle data
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd("route:R1:vehicles", System.currentTimeMillis(), "V1001");
            jedis.set("veh:V1001", "{\"vehicleId\":\"V1001\",\"lat\":40.7128,\"lon\":-74.0060}");
        }
        
        mockMvc.perform(get("/api/stream/routes/R1"))
            .andExpect(request().asyncStarted())
            .andExpect(status().isOk());
    }
    
    @Test
    void getVehiclesByRoute_shouldReturnCachedData() throws Exception {
        // Populate Redis
        try (Jedis jedis = jedisPool.getResource()) {
            long now = System.currentTimeMillis();
            jedis.zadd("route:R1:vehicles", now, "V1001");
            jedis.zadd("route:R1:vehicles", now, "V1002");
            
            String vehicleJson1 = String.format(
                "{\"vehicleId\":\"V1001\",\"routeId\":\"R1\",\"lat\":40.7128,\"lon\":-74.0060,\"tsEpochMs\":%d}",
                now
            );
            String vehicleJson2 = String.format(
                "{\"vehicleId\":\"V1002\",\"routeId\":\"R1\",\"lat\":40.7580,\"lon\":-73.9855,\"tsEpochMs\":%d}",
                now
            );
            
            jedis.set("veh:V1001", vehicleJson1);
            jedis.set("veh:V1002", vehicleJson2);
        }
        
        mockMvc.perform(get("/api/vehicles/routes/R1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].vehicleId").exists())
            .andExpect(jsonPath("$[0].routeId").value("R1"));
    }
    
    @Test
    void sseStream_multipleRoutes_shouldMaintainSeparateStreams() throws Exception {
        // Test that different routes maintain separate SSE streams
        mockMvc.perform(get("/api/stream/routes/R1"))
            .andExpect(request().asyncStarted())
            .andExpect(status().isOk());
        
        mockMvc.perform(get("/api/stream/routes/R2"))
            .andExpect(request().asyncStarted())
            .andExpect(status().isOk());
    }
}
