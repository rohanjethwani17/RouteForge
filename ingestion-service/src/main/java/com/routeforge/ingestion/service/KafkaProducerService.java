package com.routeforge.ingestion.service;

import com.routeforge.common.dto.VehiclePositionEvent;
import com.routeforge.ingestion.config.IngestionProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Produces vehicle position events to Kafka
 */
@Slf4j
@Service
public class KafkaProducerService {
    
    private final KafkaTemplate<String, VehiclePositionEvent> kafkaTemplate;
    private final IngestionProperties properties;
    private final Counter eventsPublished;
    private final Counter eventsFailed;
    
    public KafkaProducerService(
            KafkaTemplate<String, VehiclePositionEvent> kafkaTemplate,
            IngestionProperties properties,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.eventsPublished = Counter.builder("routeforge.ingestion.events.published")
            .description("Total vehicle position events published to Kafka")
            .register(meterRegistry);
        this.eventsFailed = Counter.builder("routeforge.ingestion.events.failed")
            .description("Total vehicle position events that failed to publish")
            .register(meterRegistry);
    }
    
    /**
     * Publish list of vehicle position events to Kafka
     */
    public void publishEvents(List<VehiclePositionEvent> events) {
        log.info("Publishing {} events to topic: {}", events.size(), properties.getTopic());
        
        for (VehiclePositionEvent event : events) {
            publishEvent(event);
        }
    }
    
    /**
     * Publish single event with vehicleId as key for partitioning
     */
    private void publishEvent(VehiclePositionEvent event) {
        String key = event.getVehicleId();
        
        CompletableFuture<SendResult<String, VehiclePositionEvent>> future = 
            kafkaTemplate.send(properties.getTopic(), key, event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                eventsPublished.increment();
                log.debug("Published event: {} to partition: {}", 
                    event.getEventId(), result.getRecordMetadata().partition());
            } else {
                eventsFailed.increment();
                log.error("Failed to publish event: {}", event.getEventId(), ex);
            }
        });
    }
}
