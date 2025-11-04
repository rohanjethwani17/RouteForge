package com.routeforge.common.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for generating idempotent event IDs
 */
public class EventIdGenerator {
    
    private EventIdGenerator() {
        throw new IllegalStateException("Utility class");
    }
    
    /**
     * Generate event ID from feed timestamp and vehicle ID
     * Format: {feedTimestamp}:{vehicleId}
     */
    public static String generate(long feedTimestamp, String vehicleId) {
        return feedTimestamp + ":" + vehicleId;
    }
    
    /**
     * Generate SHA-256 hash of input for stable IDs
     */
    public static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes());
            return HexFormat.of().formatHex(hashBytes).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
