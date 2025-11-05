package com.routeforge.processing.consumer;

import com.routeforge.common.dto.VehiclePositionEvent;
import com.routeforge.processing.config.ProcessingProperties;
import com.routeforge.processing.service.DatabaseService;
import com.routeforge.processing.service.RedisService;
import com.routeforge.processing.service.RedisPubSubService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka consumer for vehicle position events
 * Processes events and updates Redis + PostgreSQL
 */
@Slf4j
@Component
public class VehiclePositionConsumer {
    
    private final RedisService redisService;
    private final DatabaseService databaseService;
    private final RedisPubSubService pubSubService;
    private final ProcessingProperties properties;
    private final KafkaTemplate<String, VehiclePositionEvent> kafkaTemplate;
    private final Counter eventsProcessed;
    private final Counter eventsFailed;
    private static final int MAX_CACHE_SIZE = 100_000;
    
    private final Map<String, Long> vehicleLastTimestamp = 
        Collections.synchronizedMap(
            new LinkedHashMap<String, Long>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });
    
    public VehiclePositionConsumer(
            RedisService redisService,
            DatabaseService databaseService,
            RedisPubSubService pubSubService,
            ProcessingProperties properties,
            KafkaTemplate<String, VehiclePositionEvent> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.redisService = redisService;
        this.databaseService = databaseService;
        this.pubSubService = pubSubService;
        this.properties = properties;
        this.kafkaTemplate = kafkaTemplate;
        this.eventsProcessed = Counter.builder("routeforge.processing.events.processed")
            .description("Total vehicle position events processed")
            .register(meterRegistry);
        this.eventsFailed = Counter.builder("routeforge.processing.events.failed")
            .description("Total vehicle position events that failed processing")
            .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "#{processingProperties.topic}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload List<VehiclePositionEvent> events,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            Acknowledgment acknowledgment) {
        
        log.info("Received {} events from partition {}", events.size(), partition);
        
        try {
            // Filter out-of-order events
            List<VehiclePositionEvent> validEvents = filterOutOfOrderEvents(events);
            
            if (validEvents.isEmpty()) {
                log.debug("No valid events after filtering");
                acknowledgment.acknowledge();
                return;
            }
            
            // Update Redis hot cache
            redisService.updateVehiclePositions(validEvents);
            
            // Batch insert to PostgreSQL
            databaseService.saveVehiclePositions(validEvents);
            
            // Publish update notifications for SSE streaming
            publishUpdateNotifications(validEvents);
            
            eventsProcessed.increment(validEvents.size());
            log.info("Successfully processed {} events", validEvents.size());
            
            // Commit offset
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process events batch", e);
            eventsFailed.increment(events.size());
            
            // Send to DLQ
            sendToDLQ(events);
            
            // Acknowledge to avoid reprocessing
            acknowledgment.acknowledge();
        }
    }
    
    /**
     * Filter out events that are out of order (older than last seen)
     */
    private List<VehiclePositionEvent> filterOutOfOrderEvents(List<VehiclePositionEvent> events) {
        List<VehiclePositionEvent> validEvents = new ArrayList<>();
        
        for (VehiclePositionEvent event : events) {
            String vehicleId = event.getVehicleId();
            Long lastTs = vehicleLastTimestamp.get(vehicleId);
            
            if (lastTs == null || event.getTsEpochMs() >= lastTs) {
                validEvents.add(event);
                vehicleLastTimestamp.put(vehicleId, event.getTsEpochMs());
            } else {
                log.debug("Skipping out-of-order event for vehicle: {} (ts: {} < last: {})",
                    vehicleId, event.getTsEpochMs(), lastTs);
            }
        }
        
        return validEvents;
    }
    
    /**
     * Publish update notifications via Redis Pub/Sub for SSE streaming
     */
    private void publishUpdateNotifications(List<VehiclePositionEvent> events) {
        for (VehiclePositionEvent event : events) {
            pubSubService.publishRouteUpdate(event.getRouteId(), event.getVehicleId());
        }
    }
    
    /**
     * Send failed events to dead-letter queue
     */
    private void sendToDLQ(List<VehiclePositionEvent> events) {
        for (VehiclePositionEvent event : events) {
            try {
                kafkaTemplate.send(properties.getDlqTopic(), event.getVehicleId(), event);
                log.debug("Sent event to DLQ: {}", event.getEventId());
            } catch (Exception e) {
                log.error("Failed to send event to DLQ: {}", event.getEventId(), e);
            }
        }
    }
}
