package com.routeforge.api.controller;

import com.routeforge.api.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin operations controller (JWT protected)
 * Requires SCOPE_admin or ROLE_ADMIN
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Operations", description = "Administrative endpoints (requires authentication)")
@SecurityRequirement(name = "bearer-jwt")
public class AdminController {
    
    private final AdminService adminService;
    
    @Operation(
        summary = "Clear all Redis cache",
        description = "Clears all vehicle and route cache keys from Redis. Use with caution in production."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cache cleared successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid JWT"),
        @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    })
    @DeleteMapping("/cache/all")
    @PreAuthorize("hasAuthority('SCOPE_admin') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> clearAllCache() {
        log.info("Admin: Clearing all Redis cache");
        
        int keysDeleted = adminService.clearAllCache();
        
        Map<String, Object> response = Map.of(
            "status", "success",
            "message", "Cache cleared successfully",
            "keysDeleted", keysDeleted,
            "timestamp", System.currentTimeMillis()
        );
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "Clear cache for specific route",
        description = "Clears all vehicle cache keys for a specific route"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Route cache cleared"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @DeleteMapping("/cache/routes/{routeId}")
    @PreAuthorize("hasAuthority('SCOPE_admin') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> clearRouteCache(
            @Parameter(description = "Route ID to clear cache for", required = true)
            @PathVariable String routeId) {
        
        log.info("Admin: Clearing cache for route: {}", routeId);
        
        int keysDeleted = adminService.clearRouteCache(routeId);
        
        Map<String, Object> response = Map.of(
            "status", "success",
            "message", "Route cache cleared",
            "routeId", routeId,
            "keysDeleted", keysDeleted,
            "timestamp", System.currentTimeMillis()
        );
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "Get DLQ metrics",
        description = "Returns statistics about dead-letter queue messages"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "DLQ metrics retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/dlq/metrics")
    @PreAuthorize("hasAuthority('SCOPE_admin') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getDlqMetrics() {
        log.info("Admin: Fetching DLQ metrics");
        
        Map<String, Object> metrics = adminService.getDlqMetrics();
        
        return ResponseEntity.ok(metrics);
    }
    
    @Operation(
        summary = "Trigger ingestion replay",
        description = "Triggers ingestion service to replay recent feed data. " +
                      "Useful for recovery after service downtime."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Replay triggered successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "503", description = "Ingestion service unavailable")
    })
    @PostMapping("/ingestion/replay")
    @PreAuthorize("hasAuthority('SCOPE_admin') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> triggerIngestionReplay(
            @Parameter(description = "Minutes of history to replay (default: 10)")
            @RequestParam(defaultValue = "10") int minutes) {
        
        log.info("Admin: Triggering ingestion replay for last {} minutes", minutes);
        
        boolean triggered = adminService.triggerIngestionReplay(minutes);
        
        if (triggered) {
            Map<String, Object> response = Map.of(
                "status", "accepted",
                "message", "Replay triggered successfully",
                "minutesToReplay", minutes,
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.accepted().body(response);
        } else {
            Map<String, Object> response = Map.of(
                "status", "error",
                "message", "Failed to trigger replay - ingestion service may be unavailable",
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.status(503).body(response);
        }
    }
    
    @Operation(
        summary = "Get admin statistics",
        description = "Returns overall system statistics for monitoring"
    )
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SCOPE_admin') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAdminStats() {
        log.debug("Admin: Fetching system stats");
        
        Map<String, Object> stats = adminService.getSystemStats();
        
        return ResponseEntity.ok(stats);
    }
}
