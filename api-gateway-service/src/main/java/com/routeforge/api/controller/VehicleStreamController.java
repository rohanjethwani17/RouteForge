package com.routeforge.api.controller;

import com.routeforge.api.sse.SseEmitterManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events (SSE) controller for real-time vehicle position streaming
 */
@Slf4j
@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
@Tag(name = "Real-Time Streaming", description = "Server-Sent Events for live vehicle updates")
public class VehicleStreamController {
    
    private final SseEmitterManager emitterManager;
    
    @Operation(
        summary = "Stream live vehicle updates for a route",
        description = "Opens a Server-Sent Events (SSE) connection that streams real-time vehicle position updates. " +
                      "Connection stays open for 30 minutes or until client disconnects. " +
                      "Heartbeat sent every 15 seconds to keep connection alive."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "SSE stream established successfully",
            content = @Content(mediaType = "text/event-stream", schema = @Schema(implementation = String.class))
        ),
        @ApiResponse(responseCode = "404", description = "Route not found"),
        @ApiResponse(responseCode = "429", description = "Too many connections from this IP")
    })
    @GetMapping(value = "/routes/{routeId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRouteUpdates(
            @Parameter(description = "Route ID to stream updates for", required = true)
            @PathVariable String routeId) {
        
        log.info("Opening SSE stream for route: {}", routeId);
        
        SseEmitter emitter = emitterManager.createEmitter(routeId);
        
        return emitter;
    }
    
    @Operation(
        summary = "Get streaming statistics",
        description = "Returns current statistics about active SSE connections"
    )
    @GetMapping("/stats")
    public StreamingStats getStreamingStats() {
        return new StreamingStats(
            emitterManager.getTotalActiveConnections(),
            emitterManager.getActiveRoutes()
        );
    }
    
    /**
     * DTO for streaming statistics
     */
    public record StreamingStats(
        int activeConnections,
        int activeRoutes
    ) {}
}
