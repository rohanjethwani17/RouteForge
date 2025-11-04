package com.routeforge.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventIdGeneratorTest {
    
    @Test
    void testGenerateEventId() {
        long timestamp = 1704067200000L;
        String vehicleId = "VEHICLE_123";
        
        String eventId = EventIdGenerator.generate(timestamp, vehicleId);
        
        assertEquals("1704067200000:VEHICLE_123", eventId);
    }
    
    @Test
    void testHashConsistency() {
        String input = "test_input";
        
        String hash1 = EventIdGenerator.hash(input);
        String hash2 = EventIdGenerator.hash(input);
        
        assertEquals(hash1, hash2, "Hash should be consistent");
        assertEquals(16, hash1.length(), "Hash should be 16 characters");
    }
    
    @Test
    void testHashUniqueness() {
        String hash1 = EventIdGenerator.hash("input1");
        String hash2 = EventIdGenerator.hash("input2");
        
        assertNotEquals(hash1, hash2, "Different inputs should produce different hashes");
    }
}
