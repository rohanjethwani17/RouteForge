package com.routeforge.api.integration;

import com.routeforge.common.dto.VehicleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for API Gateway
 * Tests REST endpoints with actual Redis and PostgreSQL
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ApiGatewayIntegrationTest {
    
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
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("routeforge.redis.host", redis::getHost);
        registry.add("routeforge.redis.port", redis::getFirstMappedPort);
        registry.add("spring.profiles.active", () -> "dev");
    }
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private JedisPool jedisPool;
    
    @Test
    void testHealthEndpoint() {
        // When
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            "/api/health",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {}
        );
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("api-gateway", response.getBody().get("service"));
    }
    
    @Test
    void testGetVehicleById_NotFound() {
        // When
        ResponseEntity<VehicleResponse> response = restTemplate.getForEntity(
            "/api/vehicles/NONEXISTENT",
            VehicleResponse.class
        );
        
        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
    
    @Test
    void testGetVehicleById_Found() {
        // Given: Vehicle data in Redis
        String vehicleId = "TEST_VEHICLE_001";
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> vehicleData = new HashMap<>();
            vehicleData.put("vehicleId", vehicleId);
            vehicleData.put("routeId", "1");
            vehicleData.put("lat", "40.7128");
            vehicleData.put("lon", "-74.0060");
            vehicleData.put("speedKph", "25.5");
            vehicleData.put("tsEpochMs", String.valueOf(System.currentTimeMillis()));
            
            jedis.hset("veh:" + vehicleId, vehicleData);
        }
        
        // When
        ResponseEntity<VehicleResponse> response = restTemplate.getForEntity(
            "/api/vehicles/" + vehicleId,
            VehicleResponse.class
        );
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(vehicleId, response.getBody().getVehicleId());
        assertEquals("1", response.getBody().getRouteId());
        assertEquals(40.7128, response.getBody().getLat());
    }
    
    @Test
    void testGetVehiclesByRoute_NoVehicles() {
        // When
        ResponseEntity<List<VehicleResponse>> response = restTemplate.exchange(
            "/api/routes/EMPTY_ROUTE/vehicles",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {}
        );
        
        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
    
    @Test
    void testGetVehiclesByRoute_MultipleVehicles() {
        // Given: Multiple vehicles for route
        String routeId = "TEST_ROUTE_1";
        
        try (Jedis jedis = jedisPool.getResource()) {
            // Add vehicles to route sorted set
            jedis.zadd("route:" + routeId + ":vehicles", 
                System.currentTimeMillis(), "TEST_V1");
            jedis.zadd("route:" + routeId + ":vehicles", 
                System.currentTimeMillis() + 1000, "TEST_V2");
            
            // Add vehicle data
            Map<String, String> v1 = new HashMap<>();
            v1.put("vehicleId", "TEST_V1");
            v1.put("routeId", routeId);
            v1.put("lat", "40.7128");
            v1.put("lon", "-74.0060");
            v1.put("tsEpochMs", String.valueOf(System.currentTimeMillis()));
            jedis.hset("veh:TEST_V1", v1);
            
            Map<String, String> v2 = new HashMap<>();
            v2.put("vehicleId", "TEST_V2");
            v2.put("routeId", routeId);
            v2.put("lat", "40.7589");
            v2.put("lon", "-73.9851");
            v2.put("tsEpochMs", String.valueOf(System.currentTimeMillis()));
            jedis.hset("veh:TEST_V2", v2);
        }
        
        // When
        ResponseEntity<List<VehicleResponse>> response = restTemplate.exchange(
            "/api/routes/" + routeId + "/vehicles",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {}
        );
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }
    
    @Test
    void testStreamingStatsEndpoint() {
        // When
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            "/api/stream/stats",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {}
        );
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("activeConnections"));
        assertTrue(response.getBody().containsKey("activeRoutes"));
    }
}
