package com.routeforge.ingestion.service;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.routeforge.ingestion.config.IngestionProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

/**
 * Fetches GTFS-Realtime feed from external URL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GtfsRealtimeFetcher {
    
    private final IngestionProperties properties;
    private final WebClient webClient = WebClient.builder().build();
    
    /**
     * Fetch GTFS-RT feed with circuit breaker and retry
     */
    @CircuitBreaker(name = "gtfsFeed", fallbackMethod = "fetchFeedFallback")
    @Retry(name = "gtfsFeed")
    public Optional<FeedMessage> fetchFeed() {
        log.debug("Fetching GTFS-RT feed from: {}", properties.getFeedUrl());
        
        try {
            byte[] feedData = webClient.get()
                .uri(properties.getFeedUrl())
                .retrieve()
                .onStatus(HttpStatus::isError, response -> {
                    log.error("HTTP error fetching feed: {}", response.statusCode());
                    return Mono.error(new RuntimeException("HTTP error: " + response.statusCode()));
                })
                .bodyToMono(byte[].class)
                .timeout(Duration.ofSeconds(properties.getTimeoutSec()))
                .block();
            
            if (feedData == null || feedData.length == 0) {
                log.warn("Empty feed data received");
                return Optional.empty();
            }
            
            FeedMessage feedMessage = FeedMessage.parseFrom(feedData);
            log.info("Successfully fetched feed with {} entities", feedMessage.getEntityCount());
            return Optional.of(feedMessage);
            
        } catch (Exception e) {
            log.error("Failed to fetch GTFS-RT feed", e);
            throw new RuntimeException("Failed to fetch feed", e);
        }
    }
    
    /**
     * Fallback when circuit is open
     */
    @SuppressWarnings("unused")
    private Optional<FeedMessage> fetchFeedFallback(Exception e) {
        log.error("Circuit breaker activated - skipping feed fetch", e);
        return Optional.empty();
    }
}
