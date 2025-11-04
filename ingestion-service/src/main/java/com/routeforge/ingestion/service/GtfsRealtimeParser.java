package com.routeforge.ingestion.service;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.routeforge.common.dto.VehiclePositionEvent;
import com.routeforge.common.util.EventIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses GTFS-Realtime protobuf feed into VehiclePositionEvent DTOs
 */
@Slf4j
@Service
public class GtfsRealtimeParser {
    
    /**
     * Parse GTFS-RT FeedMessage into list of VehiclePositionEvent
     */
    public List<VehiclePositionEvent> parse(FeedMessage feedMessage) {
        List<VehiclePositionEvent> events = new ArrayList<>();
        long feedTimestamp = feedMessage.getHeader().getTimestamp() * 1000; // Convert to ms
        
        log.debug("Parsing feed with {} entities, timestamp: {}", 
            feedMessage.getEntityCount(), feedTimestamp);
        
        for (FeedEntity entity : feedMessage.getEntityList()) {
            if (!entity.hasVehicle()) {
                continue;
            }
            
            VehiclePosition vehicle = entity.getVehicle();
            
            // Skip if no position data
            if (!vehicle.hasPosition()) {
                log.debug("Skipping entity {} - no position data", entity.getId());
                continue;
            }
            
            try {
                VehiclePositionEvent event = buildEvent(entity.getId(), vehicle, feedTimestamp);
                events.add(event);
            } catch (Exception e) {
                log.error("Failed to parse vehicle entity: {}", entity.getId(), e);
            }
        }
        
        log.info("Parsed {} vehicle positions from feed", events.size());
        return events;
    }
    
    private VehiclePositionEvent buildEvent(String entityId, VehiclePosition vehicle, long feedTimestamp) {
        Position pos = vehicle.getPosition();
        
        // Extract vehicle ID
        String vehicleId = vehicle.hasVehicle() && vehicle.getVehicle().hasId()
            ? vehicle.getVehicle().getId()
            : entityId;
        
        // Extract route ID
        String routeId = vehicle.hasTrip() && vehicle.getTrip().hasRouteId()
            ? vehicle.getTrip().getRouteId()
            : "UNKNOWN";
        
        // Timestamp: prefer vehicle timestamp, fallback to feed timestamp
        long timestamp = vehicle.hasTimestamp() 
            ? vehicle.getTimestamp() * 1000 
            : feedTimestamp;
        
        // Generate idempotent event ID
        String eventId = EventIdGenerator.generate(feedTimestamp, vehicleId);
        
        return VehiclePositionEvent.builder()
            .eventId(eventId)
            .vehicleId(vehicleId)
            .routeId(routeId)
            .lat(pos.getLatitude())
            .lon(pos.getLongitude())
            .speedKph(pos.hasSpeed() ? (double) pos.getSpeed() * 3.6 : null) // m/s to km/h
            .headingDeg(pos.hasBearing() ? (double) pos.getBearing() : null)
            .tsEpochMs(timestamp)
            .stopId(vehicle.hasStopId() ? vehicle.getStopId() : null)
            .delaySec(vehicle.hasCurrentStatus() && vehicle.getCurrentStatus().getNumber() > 0 ? 0 : null)
            .build();
    }
}
