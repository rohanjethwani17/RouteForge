package com.routeforge.api.service;

import com.routeforge.api.config.RedisProperties;
import com.routeforge.common.dto.VehicleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for retrieving vehicle positions from Redis cache
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleService {
    
    private final JedisPool jedisPool;
    
    /**
     * Get vehicle position by vehicleId from Redis
     */
    public Optional<VehicleResponse> getVehicleById(String vehicleId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "veh:" + vehicleId;
            Map<String, String> fields = jedis.hgetAll(key);
            
            if (fields.isEmpty()) {
                log.debug("Vehicle not found in cache: {}", vehicleId);
                return Optional.empty();
            }
            
            return Optional.of(mapToVehicleResponse(fields));
            
        } catch (Exception e) {
            log.error("Failed to get vehicle from Redis: {}", vehicleId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Get all vehicles for a route from Redis
     */
    public List<VehicleResponse> getVehiclesByRoute(String routeId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String routeKey = "route:" + routeId + ":vehicles";
            
            // Get vehicle IDs from sorted set (most recent first)
            Set<String> vehicleIds = jedis.zrevrange(routeKey, 0, -1);
            
            if (vehicleIds.isEmpty()) {
                log.debug("No vehicles found for route: {}", routeId);
                return List.of();
            }
            
            List<VehicleResponse> vehicles = new ArrayList<>();
            for (String vehicleId : vehicleIds) {
                getVehicleById(vehicleId).ifPresent(vehicles::add);
            }
            
            log.debug("Found {} vehicles for route: {}", vehicles.size(), routeId);
            return vehicles;
            
        } catch (Exception e) {
            log.error("Failed to get vehicles for route: {}", routeId, e);
            return List.of();
        }
    }
    
    private VehicleResponse mapToVehicleResponse(Map<String, String> fields) {
    String latStr = fields.get("lat");
    String lonStr = fields.get("lon");
    String tsStr  = fields.get("tsEpochMs");

    // Validate presence of required fields
    if (latStr == null || lonStr == null || tsStr == null) {
        log.warn("Missing required fields for vehicle: {}", fields);
        throw new IllegalArgumentException("Missing required vehicle fields");
    }

    try {
        return VehicleResponse.builder()
            .vehicleId(fields.get("vehicleId"))
            .routeId(fields.get("routeId"))
            .lat(Double.parseDouble(latStr))
            .lon(Double.parseDouble(lonStr))
            .speedKph(fields.containsKey("speedKph")   ? Double.parseDouble(fields.get("speedKph")) : null)
            .headingDeg(fields.containsKey("headingDeg") ? Double.parseDouble(fields.get("headingDeg")) : null)
            .timestamp(Instant.ofEpochMilli(Long.parseLong(tsStr)))
            .stopId(fields.get("stopId"))
            .delaySec(fields.containsKey("delaySec")   ? Integer.parseInt(fields.get("delaySec")) : null)
            .build();
    } catch (NumberFormatException e) {
        log.warn("Invalid numeric data in Redis fields: {}", fields, e);
        throw new IllegalArgumentException("Invalid numeric data in Redis", e);
    }
}
