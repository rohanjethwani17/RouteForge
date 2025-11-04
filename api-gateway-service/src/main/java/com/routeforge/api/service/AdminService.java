package com.routeforge.api.service;

import com.routeforge.api.config.KafkaAdminConfig;
import com.routeforge.api.sse.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Admin operations service for cache management and system operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {
    
    private final JedisPool jedisPool;
    private final SseEmitterManager sseEmitterManager;
    private final AdminClient kafkaAdminClient;
    private final KafkaAdminConfig kafkaAdminConfig;
    
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
     * Get DLQ metrics from Kafka topic using Kafka Admin API
     * Returns real-time statistics about dead-letter queue messages
     */
    public Map<String, Object> getDlqMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        String dlqTopic = kafkaAdminConfig.getDlqTopic();
        
        metrics.put("dlqTopic", dlqTopic);
        metrics.put("timestamp", System.currentTimeMillis());
        
        try {
            // Get topic description to find partitions
            TopicDescription topicDescription = kafkaAdminClient
                .describeTopics(Collections.singleton(dlqTopic))
                .all()
                .get(5, TimeUnit.SECONDS)
                .get(dlqTopic);
            
            int partitionCount = topicDescription.partitions().size();
            metrics.put("partitionCount", partitionCount);
            
            // Get offsets for all partitions
            Map<TopicPartition, OffsetSpec> latestOffsetRequest = new HashMap<>();
            Map<TopicPartition, OffsetSpec> earliestOffsetRequest = new HashMap<>();
            
            for (int i = 0; i < partitionCount; i++) {
                TopicPartition tp = new TopicPartition(dlqTopic, i);
                latestOffsetRequest.put(tp, OffsetSpec.latest());
                earliestOffsetRequest.put(tp, OffsetSpec.earliest());
            }
            
            // Fetch latest offsets
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets = 
                kafkaAdminClient.listOffsets(latestOffsetRequest)
                    .all()
                    .get(5, TimeUnit.SECONDS);
            
            // Fetch earliest offsets
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> earliestOffsets = 
                kafkaAdminClient.listOffsets(earliestOffsetRequest)
                    .all()
                    .get(5, TimeUnit.SECONDS);
            
            // Calculate total message count
            long totalMessages = 0;
            Map<Integer, Map<String, Long>> partitionMetrics = new HashMap<>();
            
            for (int i = 0; i < partitionCount; i++) {
                TopicPartition tp = new TopicPartition(dlqTopic, i);
                long latest = latestOffsets.get(tp).offset();
                long earliest = earliestOffsets.get(tp).offset();
                long count = latest - earliest;
                totalMessages += count;
                
                Map<String, Long> partitionInfo = new HashMap<>();
                partitionInfo.put("earliestOffset", earliest);
                partitionInfo.put("latestOffset", latest);
                partitionInfo.put("messageCount", count);
                partitionMetrics.put(i, partitionInfo);
            }
            
            metrics.put("totalMessages", totalMessages);
            metrics.put("partitions", partitionMetrics);
            metrics.put("status", "success");
            
            // Add helpful instructions
            metrics.put("instructions", Map.of(
                "consume", "kafka-console-consumer --bootstrap-server " + 
                    kafkaAdminConfig.getBootstrapServers() + 
                    " --topic " + dlqTopic + " --from-beginning",
                "count", "Total messages currently in DLQ: " + totalMessages
            ));
            
            log.info("Retrieved DLQ metrics: {} total messages across {} partitions", 
                totalMessages, partitionCount);
            
        } catch (Exception e) {
            log.error("Failed to retrieve DLQ metrics from Kafka", e);
            metrics.put("status", "error");
            metrics.put("error", e.getMessage());
            metrics.put("note", "Failed to connect to Kafka. Ensure Kafka is running and accessible.");
        }
        
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
