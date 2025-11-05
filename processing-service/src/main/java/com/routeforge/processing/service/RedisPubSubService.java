package com.routeforge.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Pub/Sub service for publishing vehicle update notifications
 * Enables real-time SSE streaming in API Gateway
 */
@Slf4j
@Service
public class RedisPubSubService {
    
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final Counter notificationsPublished;
    
    public RedisPubSubService(
            JedisPool jedisPool,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
        this.notificationsPublished = Counter.builder("routeforge.processing.pubsub.published")
            .description("Total update notifications published to Redis Pub/Sub")
            .register(meterRegistry);
    }
    
    /**
     * Publish update notification for a route
     * Channel: route:{routeId}:updates
     * Payload: {routeId, vehicleId, updatedAt}
     */
    public void publishRouteUpdate(String routeId, String vehicleId) {
        if (routeId == null || vehicleId == null) {
            log.warn("Cannot publish update with null routeId or vehicleId");
            return;
        }
        
        try (Jedis jedis = jedisPool.getResource()) {
            String channel = "route:" + routeId + ":updates";
            
            Map<String, Object> notification = new HashMap<>();
            notification.put("routeId", routeId);
            notification.put("vehicleId", vehicleId);
            notification.put("updatedAt", Instant.now().toEpochMilli());
            
            String message = objectMapper.writeValueAsString(notification);
            long subscribers = jedis.publish(channel, message);
            
            notificationsPublished.increment();
            log.debug("Published update to channel {} for vehicle {} ({} subscribers)", 
                channel, vehicleId, subscribers);
            
        } catch (Exception e) {
            log.error("Failed to publish update notification for route: {}, vehicle: {}", 
                routeId, vehicleId, e);
        }
    }
    
    /**
     * Publish bulk updates for multiple vehicles (batch optimization)
     */
    public void publishBulkRouteUpdates(Map<String, String> routeVehicleMap) {
        routeVehicleMap.forEach(this::publishRouteUpdate);
    }
}
