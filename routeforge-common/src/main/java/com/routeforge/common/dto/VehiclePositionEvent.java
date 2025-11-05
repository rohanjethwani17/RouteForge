package com.routeforge.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Event representing a vehicle position update from GTFS-Realtime feed.
 * This is the core event schema used across Kafka topics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehiclePositionEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique event identifier (feedTimestamp + vehicleId) for idempotency
     */
    @NotBlank(message = "eventId must not be blank")
    @JsonProperty("eventId")
    private String eventId;
    
    /**
     * Unique vehicle identifier
     */
    @NotBlank(message = "vehicleId must not be blank")
    @JsonProperty("vehicleId")
    private String vehicleId;
    
    /**
     * Route identifier the vehicle is serving
     */
    @NotBlank(message = "routeId must not be blank")
    @JsonProperty("routeId")
    private String routeId;
    
    /**
     * Latitude in decimal degrees
     */
    @NotNull(message = "lat must not be null")
    @JsonProperty("lat")
    private Double lat;
    
    /**
     * Longitude in decimal degrees
     */
    @NotNull(message = "lon must not be null")
    @JsonProperty("lon")
    private Double lon;
    
    /**
     * Speed in kilometers per hour (optional)
     */
    @JsonProperty("speedKph")
    private Double speedKph;
    
    /**
     * Heading in degrees (0-360, optional)
     */
    @JsonProperty("headingDeg")
    private Double headingDeg;
    
    /**
     * Timestamp of the position in epoch milliseconds
     */
    @NotNull(message = "tsEpochMs must not be null")
    @JsonProperty("tsEpochMs")
    private Long tsEpochMs;
    
    /**
     * Stop ID if vehicle is at/near a stop (optional)
     */
    @JsonProperty("stopId")
    private String stopId;
    
    /**
     * Delay in seconds relative to schedule (optional, negative = early)
     */
    @JsonProperty("delaySec")
    private Integer delaySec;
    
    /**
     * Agency ID (optional)
     */
    @JsonProperty("agencyId")
    private String agencyId;
}
