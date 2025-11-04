package com.routeforge.api.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task to send heartbeat to all active SSE connections
 * Prevents connection timeout and detects dead connections
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {
    
    private final SseEmitterManager emitterManager;
    
    /**
     * Send heartbeat every 15 seconds to all active emitters
     */
    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void sendHeartbeat() {
        int totalConnections = emitterManager.getTotalActiveConnections();
        if (totalConnections > 0) {
            log.debug("Sending heartbeat to {} active SSE connections", totalConnections);
            emitterManager.sendHeartbeat();
        }
    }
}
