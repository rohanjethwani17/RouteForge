package com.routeforge.ingestion.service;

import com.google.transit.realtime.GtfsRealtime.*;
import com.routeforge.common.dto.VehiclePositionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GtfsRealtimeParserTest {
    
    private GtfsRealtimeParser parser;
    
    @BeforeEach
    void setUp() {
        parser = new GtfsRealtimeParser();
    }
    
    @Test
    void testParseEmptyFeed() {
        FeedMessage emptyFeed = FeedMessage.newBuilder()
            .setHeader(FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("2.0")
                .setTimestamp(1704067200L)
                .build())
            .build();
        
        List<VehiclePositionEvent> events = parser.parse(emptyFeed);
        
        assertTrue(events.isEmpty(), "Empty feed should produce no events");
    }
    
    @Test
    void testParseValidVehiclePosition() {
        FeedMessage feed = FeedMessage.newBuilder()
            .setHeader(FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("2.0")
                .setTimestamp(1704067200L)
                .build())
            .addEntity(FeedEntity.newBuilder()
                .setId("entity1")
                .setVehicle(VehiclePosition.newBuilder()
                    .setVehicle(VehicleDescriptor.newBuilder()
                        .setId("VEHICLE_123")
                        .build())
                    .setTrip(TripDescriptor.newBuilder()
                        .setRouteId("1")
                        .build())
                    .setPosition(Position.newBuilder()
                        .setLatitude(40.7128f)
                        .setLongitude(-74.0060f)
                        .setSpeed(7.0f) // m/s
                        .setBearing(90.0f)
                        .build())
                    .setTimestamp(1704067200L)
                    .build())
                .build())
            .build();
        
        List<VehiclePositionEvent> events = parser.parse(feed);
        
        assertEquals(1, events.size());
        
        VehiclePositionEvent event = events.get(0);
        assertEquals("VEHICLE_123", event.getVehicleId());
        assertEquals("1", event.getRouteId());
        assertEquals(40.7128, event.getLat(), 0.0001);
        assertEquals(-74.0060, event.getLon(), 0.0001);
        assertEquals(25.2, event.getSpeedKph(), 0.1); // 7 m/s = 25.2 km/h
        assertEquals(90.0, event.getHeadingDeg(), 0.1);
        assertEquals(1704067200000L, event.getTsEpochMs());
        assertNotNull(event.getEventId());
    }
    
    @Test
    void testParseVehicleWithoutPosition() {
        FeedMessage feed = FeedMessage.newBuilder()
            .setHeader(FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("2.0")
                .setTimestamp(1704067200L)
                .build())
            .addEntity(FeedEntity.newBuilder()
                .setId("entity1")
                .setVehicle(VehiclePosition.newBuilder()
                    .setVehicle(VehicleDescriptor.newBuilder()
                        .setId("VEHICLE_123")
                        .build())
                    .build())
                .build())
            .build();
        
        List<VehiclePositionEvent> events = parser.parse(feed);
        
        assertTrue(events.isEmpty(), "Vehicle without position should be skipped");
    }
    
    @Test
    void testParseMultipleVehicles() {
        FeedMessage feed = FeedMessage.newBuilder()
            .setHeader(FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("2.0")
                .setTimestamp(1704067200L)
                .build())
            .addEntity(createVehicleEntity("entity1", "VEHICLE_123", "1", 40.7128f, -74.0060f))
            .addEntity(createVehicleEntity("entity2", "VEHICLE_124", "1", 40.7589f, -73.9851f))
            .addEntity(createVehicleEntity("entity3", "VEHICLE_125", "2", 40.7306f, -73.9352f))
            .build();
        
        List<VehiclePositionEvent> events = parser.parse(feed);
        
        assertEquals(3, events.size());
        assertEquals("VEHICLE_123", events.get(0).getVehicleId());
        assertEquals("VEHICLE_124", events.get(1).getVehicleId());
        assertEquals("VEHICLE_125", events.get(2).getVehicleId());
    }
    
    private FeedEntity createVehicleEntity(String entityId, String vehicleId, 
                                           String routeId, float lat, float lon) {
        return FeedEntity.newBuilder()
            .setId(entityId)
            .setVehicle(VehiclePosition.newBuilder()
                .setVehicle(VehicleDescriptor.newBuilder()
                    .setId(vehicleId)
                    .build())
                .setTrip(TripDescriptor.newBuilder()
                    .setRouteId(routeId)
                    .build())
                .setPosition(Position.newBuilder()
                    .setLatitude(lat)
                    .setLongitude(lon)
                    .build())
                .setTimestamp(1704067200L)
                .build())
            .build();
    }
}
