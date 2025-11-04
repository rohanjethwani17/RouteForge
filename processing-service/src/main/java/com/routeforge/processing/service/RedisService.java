package com.routeforge.processing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeforge.common.dto.VehiclePositionEvent;
import com.routeforge.processing.config.RedisProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis service for hot vehicle position cache
 */
@Slf4j
@Service
public class RedisService {
    
    private final JedisPool jedisPool;
    private final RedisProperties redisProperties;
    private final ObjectMapper objectMapper;
    private final Counter cacheUpdates;
    private final Counter cacheErrors;
    
    public RedisService(
            JedisPool jedisPool,
            RedisProperties redisProperties,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.jedisPool = jedisPool;
        this.redisProperties = redisProperties;
        this.objectMapper = objectMapper;
        this.cacheUpdates = Counter.builder("routeforge.processing.cache.updates")
            .description("Total Redis cache updates")
            .register(meterRegistry);
        this.cacheErrors = Counter.builder("routeforge.processing.cache.errors")
            .description("Total Redis cache errors")
            .register(meterRegistry);
    }
    
    /**
     * Update vehicle positions in Redis
     * Key pattern: veh:{vehicleId} -> Hash
     * Key pattern: route:{routeId}:vehicles -> Sorted Set (score = timestamp)
     */
    public void updateVehiclePositions(List<VehiclePositionEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        
        try (Jedis jedis = jedisPool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            
            for (VehiclePositionEvent event : events) {
                try {
                    // Store vehicle position as hash
                    String vehicleKey = "veh:" + event.getVehicleId();
                    Map<String, String> fields = buildVehicleFields(event);
                    pipeline.hset(vehicleKey, fields);
                    pipeline.expire(vehicleKey, redisProperties.getTtlSec());
                    
                    // Add to route's sorted set
                    String routeKey = "route:" + event.getRouteId() + ":vehicles";
                    pipeline.zadd(routeKey, event.getTsEpochMs(), event.getVehicleId());
                    pipeline.expire(routeKey, redisProperties.getTtlSec());
                    
                    cacheUpdates.increment();
                    
                } catch (Exception e) {
                    log.error("Failed to update cache for vehicle: {}", event.getVehicleId(), e);
                    cacheErrors.increment();
                }
            }
            
            pipeline.sync();
            log.info("Updated {} vehicle positions in Redis", events.size());
            
        } catch (Exception e) {
            log.error("Redis pipeline error", e);
            cacheErrors.increment();
        }
    }
    
    private Map<String, String> buildVehicleFields(VehiclePositionEvent event) {
        Map<String, String> fields = new HashMap<>();
        fields.put("vehicleId", event.getVehicleId());
        fields.put("routeId", event.getRouteId());
        fields.put("lat", String.valueOf(event.getLat()));
        fields.put("lon", String.valueOf(event.getLon()));
        fields.put("tsEpochMs", String.valueOf(event.getTsEpochMs()));
        
        if (event.getSpeedKph() != null) {
            fields.put("speedKph", String.valueOf(event.getSpeedKph()));
        }
        if (event.getHeadingDeg() != null) {
            fields.put("headingDeg", String.valueOf(event.getHeadingDeg()));
        }
        if (event.getStopId() != null) {
            fields.put("stopId", event.getStopId());
        }
        if (event.getDelaySec() != null) {
            fields.put("delaySec", String.valueOf(event.getDelaySec()));
        }
        
        return fields;
    }
}
