package com.routeforge.processing.controller;

import com.routeforge.processing.service.DlqReplayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for DLQ replay operations
 * Internal endpoint - should be called by api-gateway-service
 */
@Slf4j
@RestController
@RequestMapping("/internal/dlq")
@RequiredArgsConstructor
public class DlqReplayController {
    
    private final DlqReplayService dlqReplayService;
    
    /**
     * Trigger DLQ replay
     * @param maxMessages Maximum number of messages to replay (default: 100, 0 = all)
     */
    @PostMapping("/replay")
    public ResponseEntity<Map<String, Object>> replayDlq(
            @RequestParam(defaultValue = "100") int maxMessages) {
        
        log.info("Received DLQ replay request: maxMessages={}", maxMessages);
        
        try {
            int replayed = dlqReplayService.replayDlqMessages(maxMessages);
            
            Map<String, Object> response = Map.of(
                "status", "success",
                "messagesReplayed", replayed,
                "maxMessages", maxMessages,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("DLQ replay failed", e);
            
            Map<String, Object> response = Map.of(
                "status", "error",
                "message", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.status(500).body(response);
        }
    }
}
