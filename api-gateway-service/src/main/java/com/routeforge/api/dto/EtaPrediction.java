package com.routeforge.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * ETA prediction for a vehicle arriving at a stop
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EtaPrediction {
    
    @JsonProperty("vehicleId")
    private String vehicleId;
    
    @JsonProperty("routeId")
    private String routeId;
    
    @JsonProperty("stopId")
    private String stopId;
    
    @JsonProperty("currentLat")
    private Double currentLat;
    
    @JsonProperty("currentLon")
    private Double currentLon;
    
    @JsonProperty("stopLat")
    private Double stopLat;
    
    @JsonProperty("stopLon")
    private Double stopLon;
    
    @JsonProperty("distanceKm")
    private Double distanceKm;
    
    @JsonProperty("scheduledArrival")
    private Instant scheduledArrival;
    
    @JsonProperty("predictedArrival")
    private Instant predictedArrival;
    
    @JsonProperty("delaySeconds")
    private Integer delaySeconds;
    
    @JsonProperty("confidence")
    private Double confidence;
    
    @JsonProperty("calculatedAt")
    private Instant calculatedAt;
    
    @JsonProperty("note")
    private String note;
}
