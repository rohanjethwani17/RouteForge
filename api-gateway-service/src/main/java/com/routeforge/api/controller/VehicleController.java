package com.routeforge.api.controller;

import com.routeforge.api.dto.EtaPrediction;
import com.routeforge.api.service.EtaCalculationService;
import com.routeforge.api.service.VehicleService;
import com.routeforge.common.dto.ErrorResponse;
import com.routeforge.common.dto.VehicleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * REST API for vehicle positions
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Vehicles", description = "Vehicle position tracking APIs")
public class VehicleController {
    
    private final VehicleService vehicleService;
    
    @Operation(summary = "Get vehicles by route", description = "Retrieve all active vehicles for a specific route")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful operation"),
        @ApiResponse(responseCode = "404", description = "Route not found", 
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @GetMapping("/routes/{routeId}/vehicles")
    public ResponseEntity<List<VehicleResponse>> getVehiclesByRoute(
            @PathVariable String routeId) {
        
        log.debug("GET /api/routes/{}/vehicles", routeId);
        
        List<VehicleResponse> vehicles = vehicleService.getVehiclesByRoute(routeId);
        
        if (vehicles.isEmpty()) {
            log.debug("No vehicles found for route: {}", routeId);
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(vehicles);
    }
    
    @Operation(summary = "Get vehicle by ID", description = "Retrieve current position of a specific vehicle")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful operation"),
        @ApiResponse(responseCode = "404", description = "Vehicle not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @GetMapping("/vehicles/{vehicleId}")
    public ResponseEntity<VehicleResponse> getVehicleById(
            @PathVariable String vehicleId) {
        
        log.debug("GET /api/vehicles/{}", vehicleId);
        
        return vehicleService.getVehicleById(vehicleId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @Operation(summary = "Health check", description = "API health status")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new java.util.HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now());
        health.put("service", "api-gateway");
        return ResponseEntity.ok(health);
    }
}
