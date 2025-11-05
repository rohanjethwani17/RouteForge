package com.routeforge.ingestion.scheduler;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.routeforge.common.dto.VehiclePositionEvent;
import com.routeforge.ingestion.config.IngestionProperties;
import com.routeforge.ingestion.service.GtfsRealtimeFetcher;
import com.routeforge.ingestion.service.GtfsRealtimeParser;
import com.routeforge.ingestion.service.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled task to poll GTFS-RT feed and publish to Kafka
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "routeforge.ingestion", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FeedIngestionScheduler {
    
    private final GtfsRealtimeFetcher fetcher;
    private final GtfsRealtimeParser parser;
    private final KafkaProducerService producerService;
    private final IngestionProperties properties;
    
    /**
     * Poll GTFS-RT feed at configured interval
     */
    @Scheduled(fixedDelayString = "#{${GTFS_RT_POLL_INTERVAL_SEC:5} * 1000}", 
               initialDelay = 5000)
    public void ingestFeed() {
        log.debug("Starting feed ingestion cycle");
        
        try {
            // Fetch feed
            Optional<FeedMessage> feedMessage = fetcher.fetchFeed();
            if (feedMessage.isEmpty()) {
                log.warn("No feed data available - skipping cycle");
                return;
            }
            
            // Parse feed
            List<VehiclePositionEvent> events = parser.parse(feedMessage.get());
            if (events.isEmpty()) {
                log.info("No vehicle positions found in feed");
                return;
            }
            
            // Publish to Kafka
            producerService.publishEvents(events);
            
            log.info("Ingestion cycle completed - {} events published", events.size());
            
        } catch (Exception e) {
            log.error("Error during feed ingestion cycle", e);
        }
    }
}
