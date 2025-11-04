package com.routeforge.api.service;

import com.routeforge.api.dto.EtaPrediction;
import com.routeforge.common.dto.VehicleResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EtaCalculationServiceTest {
    
    private JdbcTemplate jdbcTemplate;
    private VehicleService vehicleService;
    private EtaCalculationService etaService;
    
    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        vehicleService = mock(VehicleService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        etaService = new EtaCalculationService(jdbcTemplate, vehicleService, meterRegistry);
    }
    
    @Test
    void testCalculateEta_WithHistoricalData() {
        // Given
        String routeId = "1";
        String stopId = "STOP_456";
        
        VehicleResponse vehicle = VehicleResponse.builder()
            .vehicleId("VEHICLE_123")
            .routeId(routeId)
            .lat(40.7128)
            .lon(-74.0060)
            .speedKph(25.0)
            .timestamp(Instant.now())
            .build();
        
        when(vehicleService.getVehiclesByRoute(routeId))
            .thenReturn(List.of(vehicle));
        
        // Mock historical speed query
        when(jdbcTemplate.queryForObject(anyString(), eq(Double.class), eq(routeId), anyInt()))
            .thenReturn(30.0); // 30 km/h average
        
        // Mock sample count query
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(routeId), anyInt()))
            .thenReturn(20); // 20 samples
        
        // When
        List<EtaPrediction> predictions = etaService.calculateEtaForRoute(routeId, stopId);
        
        // Then
        assertEquals(1, predictions.size());
        
        EtaPrediction prediction = predictions.get(0);
        assertEquals("VEHICLE_123", prediction.getVehicleId());
        assertEquals(routeId, prediction.getRouteId());
        assertEquals(stopId, prediction.getStopId());
        assertNotNull(prediction.getPredictedArrival());
        assertNotNull(prediction.getDistanceKm());
        assertTrue(prediction.getConfidence() > 0.5, "Confidence should be high with 20 samples");
        assertNull(prediction.getNote(), "Should have no warning with sufficient samples");
    }
    
    @Test
    void testCalculateEta_LowConfidence() {
        // Given
        String routeId = "NEW_ROUTE";
        String stopId = "STOP_789";
        
        VehicleResponse vehicle = VehicleResponse.builder()
            .vehicleId("VEHICLE_999")
            .routeId(routeId)
            .lat(40.7300)
            .lon(-73.9900)
            .speedKph(null) // No current speed
            .timestamp(Instant.now())
            .build();
        
        when(vehicleService.getVehiclesByRoute(routeId))
            .thenReturn(List.of(vehicle));
        
        // Mock insufficient historical data
        when(jdbcTemplate.queryForObject(anyString(), eq(Double.class), eq(routeId), anyInt()))
            .thenReturn(null);
        
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(routeId), anyInt()))
            .thenReturn(2); // Only 2 samples
        
        // When
        List<EtaPrediction> predictions = etaService.calculateEtaForRoute(routeId, stopId);
        
        // Then
        assertEquals(1, predictions.size());
        
        EtaPrediction prediction = predictions.get(0);
        assertTrue(prediction.getConfidence() < 0.5, "Confidence should be low with few samples");
        assertNotNull(prediction.getNote(), "Should have warning about low confidence");
        assertTrue(prediction.getNote().contains("Low confidence"));
    }
    
    @Test
    void testCalculateEta_NoVehicles() {
        // Given
        String routeId = "EMPTY_ROUTE";
        String stopId = "STOP_000";
        
        when(vehicleService.getVehiclesByRoute(routeId))
            .thenReturn(List.of());
        
        // When
        List<EtaPrediction> predictions = etaService.calculateEtaForRoute(routeId, stopId);
        
        // Then
        assertTrue(predictions.isEmpty());
    }
    
    @Test
    void testCalculateEta_UseCurrentSpeed() {
        // Given
        String routeId = "1";
        String stopId = "STOP_123";
        
        VehicleResponse vehicle = VehicleResponse.builder()
            .vehicleId("VEHICLE_FAST")
            .routeId(routeId)
            .lat(40.7128)
            .lon(-74.0060)
            .speedKph(40.0) // Current speed available
            .timestamp(Instant.now())
            .build();
        
        when(vehicleService.getVehiclesByRoute(routeId))
            .thenReturn(List.of(vehicle));
        
        when(jdbcTemplate.queryForObject(anyString(), eq(Double.class), eq(routeId), anyInt()))
            .thenReturn(25.0); // Historical average
        
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(routeId), anyInt()))
            .thenReturn(10);
        
        // When
        List<EtaPrediction> predictions = etaService.calculateEtaForRoute(routeId, stopId);
        
        // Then
        EtaPrediction prediction = predictions.get(0);
        // Should use current speed (40 km/h) for calculation, resulting in faster ETA
        // We can't test exact ETA but confidence should be boosted
        assertTrue(prediction.getConfidence() > 0.5, "Confidence boosted by current speed");
    }
}
