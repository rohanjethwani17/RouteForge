package com.routeforge.api.service;

import com.routeforge.api.dto.EtaPrediction;
import com.routeforge.common.dto.VehicleResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ETA calculation service using historical data and current positions
 * Implements simple moving average for segment speed prediction
 */
@Slf4j
@Service
public class EtaCalculationService {
    
    private final JdbcTemplate jdbcTemplate;
    private final VehicleService vehicleService;
    private final Counter etaCalculations;
    private final Timer etaCalculationTimer;
    
    // Configuration
    private static final int HISTORICAL_WINDOW_MINUTES = 60;
    private static final int MIN_SAMPLES_FOR_CONFIDENCE = 5;
    private static final double DEFAULT_SPEED_KPH = 25.0; // Fallback speed
    
    public EtaCalculationService(
            JdbcTemplate jdbcTemplate,
            VehicleService vehicleService,
            MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.vehicleService = vehicleService;
        this.etaCalculations = Counter.builder("routeforge.api.eta.calculations")
            .description("Total ETA calculations performed")
            .register(meterRegistry);
        this.etaCalculationTimer = Timer.builder("routeforge.api.eta.calculation.time")
            .description("Time taken to calculate ETAs")
            .register(meterRegistry);
    }
    
    /**
     * Calculate ETA predictions for all vehicles on a route to a specific stop
     */
    public List<EtaPrediction> calculateEtaForRoute(String routeId, String stopId) {
        return etaCalculationTimer.record(() -> {
            log.debug("Calculating ETA for route: {}, stop: {}", routeId, stopId);
            
            List<EtaPrediction> predictions = new ArrayList<>();
            
            // Get current vehicles on route from cache
            List<VehicleResponse> vehicles = vehicleService.getVehiclesByRoute(routeId);
            
            if (vehicles.isEmpty()) {
                log.debug("No vehicles found for route: {}", routeId);
                return predictions;
            }
            
            // Get stop coordinates (simplified - in production, query stops table)
            Map<String, Double> stopLocation = getStopLocation(stopId);
            if (stopLocation == null) {
                log.warn("Stop not found: {}", stopId);
                return predictions;
            }
            
            double stopLat = stopLocation.get("lat");
            double stopLon = stopLocation.get("lon");
            
            // Calculate ETA for each vehicle
            for (VehicleResponse vehicle : vehicles) {
                try {
                    EtaPrediction prediction = calculateVehicleEta(
                        vehicle, stopId, stopLat, stopLon, routeId
                    );
                    predictions.add(prediction);
                    etaCalculations.increment();
                } catch (Exception e) {
                    log.error("Failed to calculate ETA for vehicle: {}", vehicle.getVehicleId(), e);
                }
            }
            
            log.info("Calculated {} ETA predictions for route: {}", predictions.size(), routeId);
            return predictions;
        });
    }
    
    /**
     * Calculate ETA for a single vehicle to a stop
     */
    private EtaPrediction calculateVehicleEta(
            VehicleResponse vehicle,
            String stopId,
            double stopLat,
            double stopLon,
            String routeId) {
        
        // Calculate distance from current position to stop
        double distanceKm = calculateHaversineDistance(
            vehicle.getLat(), vehicle.getLon(),
            stopLat, stopLon
        );
        
        // Get historical average speed for this route segment
        Double avgSpeedKph = getHistoricalAverageSpeed(routeId);
        int sampleCount = getHistoricalSampleCount(routeId);
        
        // Use vehicle's current speed if available, otherwise historical avg
        double speedKph = vehicle.getSpeedKph() != null && vehicle.getSpeedKph() > 0
            ? vehicle.getSpeedKph()
            : (avgSpeedKph != null ? avgSpeedKph : DEFAULT_SPEED_KPH);
        
        // Calculate travel time in seconds
        double travelTimeSeconds = (distanceKm / speedKph) * 3600;
        
        // Predicted arrival time
        Instant predictedArrival = Instant.now().plusSeconds((long) travelTimeSeconds);
        
        // Calculate confidence based on sample size and speed consistency
        double confidence = calculateConfidence(sampleCount, vehicle.getSpeedKph() != null);
        
        // Build prediction
        return EtaPrediction.builder()
            .vehicleId(vehicle.getVehicleId())
            .routeId(routeId)
            .stopId(stopId)
            .currentLat(vehicle.getLat())
            .currentLon(vehicle.getLon())
            .stopLat(stopLat)
            .stopLon(stopLon)
            .distanceKm(Math.round(distanceKm * 100.0) / 100.0)
            .predictedArrival(predictedArrival)
            .scheduledArrival(null) // Would come from GTFS static schedule
            .delaySeconds(vehicle.getDelaySec())
            .confidence(Math.round(confidence * 100.0) / 100.0)
            .calculatedAt(Instant.now())
            .note(sampleCount < MIN_SAMPLES_FOR_CONFIDENCE 
                ? "Low confidence - insufficient historical data"
                : null)
            .build();
    }
    
    /**
     * Get historical average speed for a route from last hour of data
     */
    private Double getHistoricalAverageSpeed(String routeId) {
        try {
            String sql = """
                SELECT AVG(speed_kph) as avg_speed
                FROM vehicle_positions_history
                WHERE route_id = ?
                  AND speed_kph IS NOT NULL
                  AND speed_kph > 0
                  AND recorded_at > NOW() - INTERVAL '? minutes'
                """;
            
            Double avgSpeed = jdbcTemplate.queryForObject(
                sql,
                Double.class,
                routeId,
                HISTORICAL_WINDOW_MINUTES
            );
            
            log.debug("Historical avg speed for route {}: {} km/h", routeId, avgSpeed);
            return avgSpeed;
            
        } catch (Exception e) {
            log.debug("Failed to get historical speed for route: {}", routeId, e);
            return null;
        }
    }
    
    /**
     * Get count of historical samples for confidence calculation
     */
    private int getHistoricalSampleCount(String routeId) {
        try {
            String sql = """
                SELECT COUNT(*) 
                FROM vehicle_positions_history
                WHERE route_id = ?
                  AND speed_kph IS NOT NULL
                  AND recorded_at > NOW() - INTERVAL '? minutes'
                """;
            
            Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                routeId,
                HISTORICAL_WINDOW_MINUTES
            );
            
            return count != null ? count : 0;
            
        } catch (Exception e) {
            log.debug("Failed to get sample count for route: {}", routeId, e);
            return 0;
        }
    }
    
    /**
     * Get stop location (simplified - in production, query stops table)
     */
    private Map<String, Double> getStopLocation(String stopId) {
        // Placeholder - in production:
        // SELECT lat, lon FROM stops WHERE stop_id = ?
        
        // Return mock coordinates for demonstration
        return Map.of(
            "lat", 40.7589,
            "lon", -73.9851
        );
    }
    
    /**
     * Calculate confidence score (0.0 - 1.0)
     * Based on historical sample size and data freshness
     */
    private double calculateConfidence(int sampleCount, boolean hasCurrentSpeed) {
        // Base confidence from sample size
        double sampleConfidence = Math.min(1.0, (double) sampleCount / (MIN_SAMPLES_FOR_CONFIDENCE * 2));
        
        // Boost if we have current vehicle speed
        double speedBoost = hasCurrentSpeed ? 0.2 : 0.0;
        
        return Math.min(1.0, sampleConfidence + speedBoost);
    }
    
    /**
     * Calculate Haversine distance between two points in kilometers
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // Earth radius in kilometers
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
}
