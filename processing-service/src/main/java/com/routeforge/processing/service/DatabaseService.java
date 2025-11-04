package com.routeforge.processing.service;

import com.routeforge.common.dto.VehiclePositionEvent;
import com.routeforge.processing.entity.VehiclePositionHistory;
import com.routeforge.processing.repository.VehiclePositionHistoryRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Database service for vehicle position history
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseService {
    
    private final VehiclePositionHistoryRepository repository;
    private final Counter dbInserts;
    private final Counter dbErrors;
    
    public DatabaseService(
            VehiclePositionHistoryRepository repository,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.dbInserts = Counter.builder("routeforge.processing.db.inserts")
            .description("Total database inserts")
            .register(meterRegistry);
        this.dbErrors = Counter.builder("routeforge.processing.db.errors")
            .description("Total database errors")
            .register(meterRegistry);
    }
    
    /**
     * Batch insert vehicle positions into history table
     */
    @Transactional
    public void saveVehiclePositions(List<VehiclePositionEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        
        try {
            List<VehiclePositionHistory> entities = events.stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
            
            repository.saveAll(entities);
            dbInserts.increment(entities.size());
            
            log.info("Saved {} vehicle positions to database", entities.size());
            
        } catch (Exception e) {
            log.error("Failed to save vehicle positions to database", e);
            dbErrors.increment();
            throw e;
        }
    }
    
    private VehiclePositionHistory toEntity(VehiclePositionEvent event) {
        return VehiclePositionHistory.builder()
            .eventId(event.getEventId())
            .vehicleId(event.getVehicleId())
            .routeId(event.getRouteId())
            .lat(event.getLat())
            .lon(event.getLon())
            .speedKph(event.getSpeedKph())
            .headingDeg(event.getHeadingDeg())
            .tsEpochMs(event.getTsEpochMs())
            .stopId(event.getStopId())
            .delaySec(event.getDelaySec())
            .build();
    }
}
