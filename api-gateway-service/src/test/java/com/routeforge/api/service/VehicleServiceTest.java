package com.routeforge.api.service;

import com.routeforge.common.dto.VehicleResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VehicleServiceTest {
    
    private JedisPool jedisPool;
    private Jedis jedis;
    private VehicleService vehicleService;
    
    @BeforeEach
    void setUp() {
        jedisPool = mock(JedisPool.class);
        jedis = mock(Jedis.class);
        when(jedisPool.getResource()).thenReturn(jedis);
        
        vehicleService = new VehicleService(jedisPool);
    }
    
    @AfterEach
    void tearDown() {
        if (jedis != null) {
            jedis.close();
        }
    }
    
    @Test
    void testGetVehicleById_Found() {
        // Given
        String vehicleId = "VEHICLE_123";
        Map<String, String> vehicleData = new HashMap<>();
        vehicleData.put("vehicleId", vehicleId);
        vehicleData.put("routeId", "1");
        vehicleData.put("lat", "40.7128");
        vehicleData.put("lon", "-74.0060");
        vehicleData.put("speedKph", "25.5");
        vehicleData.put("headingDeg", "90.0");
        vehicleData.put("tsEpochMs", "1704067200000");
        
        when(jedis.hgetAll("veh:" + vehicleId)).thenReturn(vehicleData);
        
        // When
        Optional<VehicleResponse> result = vehicleService.getVehicleById(vehicleId);
        
        // Then
        assertTrue(result.isPresent());
        VehicleResponse vehicle = result.get();
        assertEquals(vehicleId, vehicle.getVehicleId());
        assertEquals("1", vehicle.getRouteId());
        assertEquals(40.7128, vehicle.getLat());
        assertEquals(-74.0060, vehicle.getLon());
        assertEquals(25.5, vehicle.getSpeedKph());
    }
    
    @Test
    void testGetVehicleById_NotFound() {
        // Given
        String vehicleId = "NONEXISTENT";
        when(jedis.hgetAll("veh:" + vehicleId)).thenReturn(new HashMap<>());
        
        // When
        Optional<VehicleResponse> result = vehicleService.getVehicleById(vehicleId);
        
        // Then
        assertFalse(result.isPresent());
    }
    
    @Test
    void testGetVehiclesByRoute_MultipleVehicles() {
        // Given
        String routeId = "1";
        Set<String> vehicleIds = Set.of("VEHICLE_123", "VEHICLE_124");
        
        when(jedis.zrevrange("route:" + routeId + ":vehicles", 0, -1))
            .thenReturn(vehicleIds);
        
        // Mock vehicle data for each vehicle
        Map<String, String> vehicle1 = new HashMap<>();
        vehicle1.put("vehicleId", "VEHICLE_123");
        vehicle1.put("routeId", routeId);
        vehicle1.put("lat", "40.7128");
        vehicle1.put("lon", "-74.0060");
        vehicle1.put("tsEpochMs", "1704067200000");
        
        Map<String, String> vehicle2 = new HashMap<>();
        vehicle2.put("vehicleId", "VEHICLE_124");
        vehicle2.put("routeId", routeId);
        vehicle2.put("lat", "40.7589");
        vehicle2.put("lon", "-73.9851");
        vehicle2.put("tsEpochMs", "1704067201000");
        
        when(jedis.hgetAll("veh:VEHICLE_123")).thenReturn(vehicle1);
        when(jedis.hgetAll("veh:VEHICLE_124")).thenReturn(vehicle2);
        
        // When
        List<VehicleResponse> results = vehicleService.getVehiclesByRoute(routeId);
        
        // Then
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(v -> v.getVehicleId().equals("VEHICLE_123")));
        assertTrue(results.stream().anyMatch(v -> v.getVehicleId().equals("VEHICLE_124")));
    }
    
    @Test
    void testGetVehiclesByRoute_NoVehicles() {
        // Given
        String routeId = "EMPTY_ROUTE";
        when(jedis.zrevrange("route:" + routeId + ":vehicles", 0, -1))
            .thenReturn(Set.of());
        
        // When
        List<VehicleResponse> results = vehicleService.getVehiclesByRoute(routeId);
        
        // Then
        assertTrue(results.isEmpty());
    }
}
