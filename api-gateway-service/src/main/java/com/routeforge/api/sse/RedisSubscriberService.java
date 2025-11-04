package com.routeforge.api.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeforge.api.service.VehicleService;
import com.routeforge.common.dto.VehicleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Redis Pub/Sub subscriber that receives vehicle update notifications
 * and fans them out to SSE clients
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSubscriberService {
    
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final SseEmitterManager emitterManager;
    private final VehicleService vehicleService;
    
    private ExecutorService executorService;
    private JedisPubSub subscriber;
    
    /**
     * Start subscribing to route update channels
     * Pattern: route:*:updates
     */
    @PostConstruct
    public void start() {
        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "redis-pubsub-subscriber");
            thread.setDaemon(true);
            return thread;
        });
        
        subscriber = new JedisPubSub() {
            @Override
            public void onPMessage(String pattern, String channel, String message) {
                handleUpdateNotification(channel, message);
            }
            
            @Override
            public void onSubscribe(String channel, int subscribedChannels) {
                log.info("Subscribed to channel: {}", channel);
            }
            
            @Override
            public void onPSubscribe(String pattern, int subscribedChannels) {
                log.info("Pattern-subscribed to: {} (total subscriptions: {})", pattern, subscribedChannels);
            }
        };
        
        // Subscribe in background thread
        executorService.submit(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                log.info("Starting Redis Pub/Sub subscriber for route:*:updates");
                // Pattern subscribe to all route update channels
                jedis.psubscribe(subscriber, "route:*:updates");
            } catch (Exception e) {
                log.error("Redis Pub/Sub subscriber error", e);
            }
        });
    }
    
    /**
     * Handle incoming update notification
     */
    private void handleUpdateNotification(String channel, String message) {
        try {
            // Parse notification: {routeId, vehicleId, updatedAt}
            @SuppressWarnings("unchecked")
            Map<String, Object> notification = objectMapper.readValue(message, Map.class);
            
            String routeId = (String) notification.get("routeId");
            String vehicleId = (String) notification.get("vehicleId");
            
            if (routeId == null || vehicleId == null) {
                log.warn("Invalid notification received: {}", message);
                return;
            }
            
            log.debug("Received update notification for route: {}, vehicle: {}", routeId, vehicleId);
            
            // Fetch fresh vehicle data from Redis
            Optional<VehicleResponse> vehicleData = vehicleService.getVehicleById(vehicleId);
            
            if (vehicleData.isPresent()) {
                // Fan out to all SSE subscribers of this route
                emitterManager.sendToRoute(routeId, vehicleData.get());
            } else {
                log.warn("Vehicle data not found in cache for vehicle: {}", vehicleId);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle update notification from channel: {}", channel, e);
        }
    }
    
    /**
     * Stop subscriber on shutdown
     */
    @PreDestroy
    public void stop() {
        log.info("Stopping Redis Pub/Sub subscriber");
        
        if (subscriber != null && subscriber.isSubscribed()) {
            subscriber.punsubscribe();
        }
        
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
