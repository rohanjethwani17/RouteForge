package com.routeforge.processing.service;

import com.routeforge.common.dto.VehiclePositionEvent;
import com.routeforge.processing.config.ProcessingProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Service for replaying messages from the DLQ (Dead Letter Queue)
 */
@Slf4j
@Service
public class DlqReplayService {
    
    private final ProcessingProperties properties;
    private final RedisService redisService;
    private final DatabaseService databaseService;
    private final RedisPubSubService pubSubService;
    private final Counter replaySuccessCounter;
    private final Counter replayFailureCounter;
    
    public DlqReplayService(
            ProcessingProperties properties,
            RedisService redisService,
            DatabaseService databaseService,
            RedisPubSubService pubSubService,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.redisService = redisService;
        this.databaseService = databaseService;
        this.pubSubService = pubSubService;
        this.replaySuccessCounter = Counter.builder("routeforge.dlq.replay.success")
            .description("Successfully replayed DLQ messages")
            .register(meterRegistry);
        this.replayFailureCounter = Counter.builder("routeforge.dlq.replay.failure")
            .description("Failed to replay DLQ messages")
            .register(meterRegistry);
    }
    
    /**
     * Replay messages from DLQ
     * @param maxMessages Maximum number of messages to replay (0 = all available)
     * @return Number of messages successfully replayed
     */
    public int replayDlqMessages(int maxMessages) {
        log.info("Starting DLQ replay: maxMessages={}", maxMessages);
        
        int successCount = 0;
        int failureCount = 0;
        
        // Create temporary consumer for DLQ
        KafkaConsumer<String, VehiclePositionEvent> consumer = createDlqConsumer();
        
        try {
            consumer.subscribe(Collections.singleton(properties.getDlqTopic()));
            
            // Poll messages
            ConsumerRecords<String, VehiclePositionEvent> records = 
                consumer.poll(Duration.ofSeconds(10));
            
            if (records.isEmpty()) {
                log.info("No messages found in DLQ");
                return 0;
            }
            
            List<VehiclePositionEvent> eventsToProcess = new ArrayList<>();
            int processedCount = 0;
            
            for (ConsumerRecord<String, VehiclePositionEvent> record : records) {
                eventsToProcess.add(record.value());
                processedCount++;
            
                if (eventsToProcess.size() >= 100 ||
                    (maxMessages > 0 && processedCount >= maxMessages)) {
                    int batchSuccess = processBatch(eventsToProcess);
                    successCount += batchSuccess;
                    failureCount += (eventsToProcess.size() - batchSuccess);
                    eventsToProcess.clear();
            
                    // ✅ commit offsets for processed records
                    consumer.commitSync();
                }
            
                if (maxMessages > 0 && processedCount >= maxMessages) {
                    break;
                }
            }
            
            // Process any remaining events
            if (!eventsToProcess.isEmpty()) {
                int batchSuccess = processBatch(eventsToProcess);
                successCount += batchSuccess;
                failureCount += (eventsToProcess.size() - batchSuccess);
                // ✅ commit offsets after final batch
                consumer.commitSync();
            }

            
            log.info("DLQ replay completed: success={}, failure={}", successCount, failureCount);
            
        } catch (Exception e) {
            log.error("Error during DLQ replay", e);
            throw new RuntimeException("DLQ replay failed", e);
        } finally {
            consumer.close();
        }
        
        replaySuccessCounter.increment(successCount);
        replayFailureCounter.increment(failureCount);
        
        return successCount;
    }
    
    /**
     * Process a batch of events from DLQ
     */
    private int processBatch(List<VehiclePositionEvent> events) {
        try {
            // Update Redis
            redisService.updateVehiclePositions(events);
            
            // Save to PostgreSQL
            databaseService.saveVehiclePositions(events);
            
            // Publish update notifications
            for (VehiclePositionEvent event : events) {
                pubSubService.publishRouteUpdate(event.getRouteId(), event.getVehicleId());
            }
            
            log.debug("Successfully processed batch of {} events from DLQ", events.size());
            return events.size();
            
        } catch (Exception e) {
            log.error("Failed to process DLQ batch", e);
            return 0;
        }
    }
    
    /**
     * Create a temporary Kafka consumer for DLQ
     */
    private KafkaConsumer<String, VehiclePositionEvent> createDlqConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getKafkaBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-replay-" + System.currentTimeMillis());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.routeforge.common.dto");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, VehiclePositionEvent.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");
        
        return new KafkaConsumer<>(props);
    }
}
