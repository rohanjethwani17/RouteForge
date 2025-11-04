package com.routeforge.api.sse;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Manages SSE emitters for real-time vehicle updates
 * Thread-safe management of active connections per route
 */
@Slf4j
@Component
public class SseEmitterManager {
    
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutes
    private static final long HEARTBEAT_INTERVAL = 15000L; // 15 seconds
    
    // Map: routeId -> Set of emitters subscribed to that route
    private final Map<String, Set<SseEmitter>> routeEmitters = new ConcurrentHashMap<>();
    
    private final Counter emittersCreated;
    private final Counter emittersRemoved;
    private final Counter messagesSent;
    private final Counter messagesFailed;
    
    public SseEmitterManager(MeterRegistry meterRegistry) {
        this.emittersCreated = Counter.builder("routeforge.sse.emitters.created")
            .description("Total SSE emitters created")
            .register(meterRegistry);
        this.emittersRemoved = Counter.builder("routeforge.sse.emitters.removed")
            .description("Total SSE emitters removed")
            .register(meterRegistry);
        this.messagesSent = Counter.builder("routeforge.sse.messages.sent")
            .description("Total SSE messages sent")
            .register(meterRegistry);
        this.messagesFailed = Counter.builder("routeforge.sse.messages.failed")
            .description("Total SSE messages that failed to send")
            .register(meterRegistry);
        
        // Gauge for active connections per route
        Gauge.builder("routeforge.sse.active.connections", this, SseEmitterManager::getTotalActiveConnections)
            .description("Total active SSE connections across all routes")
            .register(meterRegistry);
    }
    
    /**
     * Create and register a new SSE emitter for a route
     */
    public SseEmitter createEmitter(String routeId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        // Register emitter
        routeEmitters.computeIfAbsent(routeId, k -> new CopyOnWriteArraySet<>()).add(emitter);
        emittersCreated.increment();
        
        log.info("Created SSE emitter for route: {} (total: {})", routeId, getActiveConnections(routeId));
        
        // Setup completion/timeout/error handlers
        emitter.onCompletion(() -> {
            removeEmitter(routeId, emitter);
            log.debug("SSE emitter completed for route: {}", routeId);
        });
        
        emitter.onTimeout(() -> {
            removeEmitter(routeId, emitter);
            log.debug("SSE emitter timed out for route: {}", routeId);
        });
        
        emitter.onError(ex -> {
            removeEmitter(routeId, emitter);
            log.warn("SSE emitter error for route: {}", routeId, ex);
        });
        
        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("{\"message\":\"Connected to route " + routeId + "\",\"timestamp\":" + System.currentTimeMillis() + "}"));
        } catch (IOException e) {
            log.error("Failed to send connection event to route: {}", routeId, e);
            removeEmitter(routeId, emitter);
        }
        
        return emitter;
    }
    
    /**
     * Remove an emitter from the registry
     */
    private void removeEmitter(String routeId, SseEmitter emitter) {
        Set<SseEmitter> emitters = routeEmitters.get(routeId);
        if (emitters != null) {
            emitters.remove(emitter);
            emittersRemoved.increment();
            if (emitters.isEmpty()) {
                routeEmitters.remove(routeId);
            }
        }
        log.debug("Removed SSE emitter for route: {} (remaining: {})", routeId, getActiveConnections(routeId));
    }
    
    /**
     * Send data to all emitters subscribed to a route
     */
    public void sendToRoute(String routeId, Object data) {
        Set<SseEmitter> emitters = routeEmitters.get(routeId);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("No subscribers for route: {}", routeId);
            return;
        }
        
        log.debug("Sending update to {} subscribers of route: {}", emitters.size(), routeId);
        
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("vehicle-update")
                    .data(data));
                messagesSent.increment();
            } catch (IOException e) {
                log.warn("Failed to send SSE message to route: {}", routeId, e);
                messagesFailed.increment();
                removeEmitter(routeId, emitter);
            }
        }
    }
    
    /**
     * Send heartbeat to all active emitters to keep connections alive
     */
    public void sendHeartbeat() {
        log.debug("Sending heartbeat to {} routes", routeEmitters.size());
        
        routeEmitters.forEach((routeId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .comment("keep-alive"));
                } catch (IOException e) {
                    log.debug("Heartbeat failed for route: {}, removing emitter", routeId);
                    removeEmitter(routeId, emitter);
                }
            }
        });
    }
    
    /**
     * Get number of active connections for a route
     */
    public int getActiveConnections(String routeId) {
        Set<SseEmitter> emitters = routeEmitters.get(routeId);
        return emitters != null ? emitters.size() : 0;
    }
    
    /**
     * Get total active connections across all routes
     */
    public int getTotalActiveConnections() {
        return routeEmitters.values().stream()
            .mapToInt(Set::size)
            .sum();
    }
    
    /**
     * Get number of routes with active subscriptions
     */
    public int getActiveRoutes() {
        return routeEmitters.size();
    }
}
