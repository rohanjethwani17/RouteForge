package com.routeforge.processing.integration;

import com.routeforge.common.dto.VehiclePositionEvent;
import com.routeforge.processing.entity.VehiclePositionHistory;
import com.routeforge.processing.repository.VehiclePositionHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Map;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

/**
 * Integration test for processing service
 * Tests end-to-end flow: Kafka -> Processing -> Redis + PostgreSQL
 */
@SpringBootTest
@Testcontainers
class ProcessingIntegrationTest {
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.3")
    );
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:16-alpine")
    )
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(
        DockerImageName.parse("redis:7.2-alpine")
    )
        .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("routeforge.redis.host", redis::getHost);
        registry.add("routeforge.redis.port", redis::getFirstMappedPort);
    }
    
    @Autowired
    private KafkaTemplate<String, VehiclePositionEvent> kafkaTemplate;
    
    @Autowired
    private VehiclePositionHistoryRepository repository;
    
    @Autowired
    private JedisPool jedisPool;
    
    @Test
    void testVehiclePositionProcessing() {
        // Given: A vehicle position event
        VehiclePositionEvent event = VehiclePositionEvent.builder()
            .eventId("test-event-1")
            .vehicleId("TEST_VEHICLE_001")
            .routeId("TEST_ROUTE_1")
            .lat(40.7128)
            .lon(-74.0060)
            .speedKph(25.5)
            .headingDeg(90.0)
            .tsEpochMs(System.currentTimeMillis())
            .build();
        
        // When: Event is published to Kafka
        kafkaTemplate.send("vehicle_positions", event.getVehicleId(), event);
        
        // Then: Event should be processed and stored in Redis
        await()
            .atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                try (Jedis jedis = jedisPool.getResource()) {
                    String vehicleKey = "veh:" + event.getVehicleId();
                    Map<String, String> vehicleData = jedis.hgetAll(vehicleKey);
                    
                    assertFalse(vehicleData.isEmpty(), "Vehicle data should exist in Redis");
                    assertEquals(event.getVehicleId(), vehicleData.get("vehicleId"));
                    assertEquals(event.getRouteId(), vehicleData.get("routeId"));
                    assertEquals(String.valueOf(event.getLat()), vehicleData.get("lat"));
                }
            });
        
        // And: Event should be stored in PostgreSQL
        await()
            .atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                Optional<VehiclePositionHistory> saved = repository.findByEventId(event.getEventId());
                
                assertTrue(saved.isPresent(), "Event should be saved to database");
                assertEquals(event.getVehicleId(), saved.get().getVehicleId());
                assertEquals(event.getRouteId(), saved.get().getRouteId());
                assertEquals(event.getLat(), saved.get().getLat());
                assertEquals(event.getLon(), saved.get().getLon());
            });
    }
    
    @Test
    void testBatchProcessing() {
        // Given: Multiple vehicle position events
        String routeId = "TEST_ROUTE_BATCH";
        
        for (int i = 0; i < 5; i++) {
            VehiclePositionEvent event = VehiclePositionEvent.builder()
                .eventId("batch-event-" + i)
                .vehicleId("BATCH_VEHICLE_" + i)
                .routeId(routeId)
                .lat(40.7128 + i * 0.01)
                .lon(-74.0060 + i * 0.01)
                .speedKph(25.0 + i)
                .tsEpochMs(System.currentTimeMillis() + i * 1000)
                .build();
            
            kafkaTemplate.send("vehicle_positions", event.getVehicleId(), event);
        }
        
        // Then: All events should be processed
        await()
            .atMost(Duration.ofSeconds(15))
            .untilAsserted(() -> {
                long count = repository.count();
                assertTrue(count >= 5, "At least 5 events should be in database");
            });
        
        // And: Route key should exist in Redis
        await()
            .atMost(Duration.ofSeconds(15))
            .untilAsserted(() -> {
                try (Jedis jedis = jedisPool.getResource()) {
                    String routeKey = "route:" + routeId + ":vehicles";
                    Long vehicleCount = jedis.zcard(routeKey);
                    
                    assertNotNull(vehicleCount);
                    assertTrue(vehicleCount >= 5, "Route should have at least 5 vehicles");
                }
            });
    }
}
