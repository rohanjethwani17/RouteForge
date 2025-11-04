package com.routeforge.api.service;

import com.routeforge.api.sse.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Admin operations service for cache management and system operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {
    
    private final JedisPool jedisPool;
    private final SseEmitterManager sseEmitterManager;
    
    /**
     * Clear all vehicle and route cache keys from Redis
     * Returns number of keys deleted
     */
    public int clearAllCache() {
        int totalDeleted = 0;
        
        try (Jedis jedis = jedisPool.getResource()) {
            // Delete vehicle keys (veh:*)
            totalDeleted += deleteKeysByPattern(jedis, "veh:*");
            
            // Delete route keys (route:*:vehicles)
            totalDeleted += deleteKeysByPattern(jedis, "route:*:vehicles");
            
            log.info("Cleared {} cache keys from Redis", totalDeleted);
            
        } catch (Exception e) {
            log.error("Failed to clear all cache", e);
        }
        
        return totalDeleted;
    }
    
    /**
     * Clear cache for a specific route
     * Returns number of keys deleted
     */
    public int clearRouteCache(String routeId) {
        int totalDeleted = 0;
        
        try (Jedis jedis = jedisPool.getResource()) {
            // Delete route key
            String routeKey = "route:" + routeId + ":vehicles";
            if (jedis.exists(routeKey)) {
                jedis.del(routeKey);
                totalDeleted++;
            }
            
            // Get all vehicle IDs for this route (before deleting)
            Set<String> vehicleIds = jedis.zrange(routeKey, 0, -1);
            
            // Delete individual vehicle keys
            for (String vehicleId : vehicleIds) {
                String vehicleKey = "veh:" + vehicleId;
                if (jedis.exists(vehicleKey)) {
                    jedis.del(vehicleKey);
                    totalDeleted++;
                }
            }
            
            log.info("Cleared {} cache keys for route: {}", totalDeleted, routeId);
            
        } catch (Exception e) {
            log.error("Failed to clear cache for route: {}", routeId, e);
        }
        
        return totalDeleted;
    }
    
    /**
     * Get DLQ metrics from Kafka topic
     * Uses Kafka Admin API to get actual partition sizes
     * 
     * NOTE: Requires Kafka Admin dependency and configuration.
     * For MVP, we provide stats from application metrics instead.
     */
    public Map<String, Object> getDlqMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        metrics.put("dlqTopic", "vehicle_positions.dlq");
        metrics.put("timestamp", System.currentTimeMillis());
        
        // In production, use Kafka Admin API:
        // try (AdminClient adminClient = AdminClient.create(kafkaProperties)) {
        //     Map<TopicPartition, OffsetSpec> requestLatestOffsets = new HashMap<>();
        //     TopicPartition tp = new TopicPartition("vehicle_positions.dlq", 0);
        //     requestLatestOffsets.put(tp, OffsetSpec.latest());
        //     
        //     Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> offsets = 
        //         adminClient.listOffsets(requestLatestOffsets).all().get();
        //     
        //     long messageCount = offsets.get(tp).offset();
        //     metrics.put("messageCount", messageCount);
        // }
        
        // For MVP: Return instructions for monitoring
        metrics.put("note", "Use Kafka Admin API or Kafka Manager to view DLQ messages");
        metrics.put("instructions", Map.of(
            "cli", "kafka-console-consumer --bootstrap-server localhost:9092 --topic vehicle_positions.dlq --from-beginning",
            "count", "kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic vehicle_positions.dlq"
        ));
        
        // Provide application-level failure metrics instead
        metrics.put("applicationMetrics", Map.of(
            "metricName", "routeforge_processing_events_failed_total",
            "endpoint", "/actuator/prometheus",
            "description", "Total failed events sent to DLQ"
        ));
        
        return metrics;
    }
    
    /**
     * Trigger ingestion replay
     * Note: This is a placeholder - actual implementation would
     * communicate with ingestion-service via REST or messaging
     */
    public boolean triggerIngestionReplay(int minutes) {
        // Placeholder - in production:
        // 1. Call ingestion-service REST endpoint
        // 2. Or publish to a control topic
        // 3. Ingestion service reads from replay buffer/file
        
        log.warn("Ingestion replay not yet implemented - would replay last {} minutes", minutes);
        
        // Return false to indicate not yet implemented
        return false;
    }
    
    /**
     * Get overall system statistics
     */
    public Map<String, Object> getSystemStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try (Jedis jedis = jedisPool.getResource()) {
            // Redis stats
            Map<String, Object> redisStats = new HashMap<>();
            redisStats.put("vehicleKeys", countKeysByPattern(jedis, "veh:*"));
            redisStats.put("routeKeys", countKeysByPattern(jedis, "route:*:vehicles"));
            redisStats.put("totalKeys", jedis.dbSize());
            stats.put("redis", redisStats);
            
            // SSE stats
            Map<String, Object> sseStats = new HashMap<>();
            sseStats.put("activeConnections", sseEmitterManager.getTotalActiveConnections());
            sseStats.put("activeRoutes", sseEmitterManager.getActiveRoutes());
            stats.put("sse", sseStats);
            
            stats.put("timestamp", System.currentTimeMillis());
            
        } catch (Exception e) {
            log.error("Failed to get system stats", e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * Delete keys matching a pattern using SCAN (safe for production)
     */
    private int deleteKeysByPattern(Jedis jedis, String pattern) {
        int deleted = 0;
        String cursor = "0";
        ScanParams scanParams = new ScanParams().match(pattern).count(100);
        
        do {
            ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
            for (String key : scanResult.getResult()) {
                jedis.del(key);
                deleted++;
            }
            cursor = scanResult.getCursor();
        } while (!"0".equals(cursor));
        
        return deleted;
    }
    
    /**
     * Count keys matching a pattern using SCAN
     */
    private long countKeysByPattern(Jedis jedis, String pattern) {
        long count = 0;
        String cursor = "0";
        ScanParams scanParams = new ScanParams().match(pattern).count(100);
        
        do {
            ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
            count += scanResult.getResult().size();
            cursor = scanResult.getCursor();
        } while (!"0".equals(cursor));
        
        return count;
    }
}
