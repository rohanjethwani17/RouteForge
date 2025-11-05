package com.routeforge.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for vehicle position API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleResponse {
    
    @JsonProperty("vehicleId")
    private String vehicleId;
    
    @JsonProperty("routeId")
    private String routeId;
    
    @JsonProperty("lat")
    private Double lat;
    
    @JsonProperty("lon")
    private Double lon;
    
    @JsonProperty("speedKph")
    private Double speedKph;
    
    @JsonProperty("headingDeg")
    private Double headingDeg;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    @JsonProperty("stopId")
    private String stopId;
    
    @JsonProperty("delaySec")
    private Integer delaySec;
}
