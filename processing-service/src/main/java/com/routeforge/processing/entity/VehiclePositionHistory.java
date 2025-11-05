package com.routeforge.processing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "vehicle_positions_history", indexes = {
    @Index(name = "idx_vehicle_ts", columnList = "vehicle_id, ts_epoch_ms"),
    @Index(name = "idx_route_ts", columnList = "route_id, ts_epoch_ms")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehiclePositionHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;
    
    @Column(name = "vehicle_id", nullable = false, length = 50)
    private String vehicleId;
    
    @Column(name = "route_id", nullable = false, length = 50)
    private String routeId;
    
    @Column(name = "lat", nullable = false)
    private Double lat;
    
    @Column(name = "lon", nullable = false)
    private Double lon;
    
    @Column(name = "speed_kph")
    private Double speedKph;
    
    @Column(name = "heading_deg")
    private Double headingDeg;
    
    @Column(name = "ts_epoch_ms", nullable = false)
    private Long tsEpochMs;
    
    @Column(name = "stop_id", length = 50)
    private String stopId;
    
    @Column(name = "delay_sec")
    private Integer delaySec;
    
    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;
}
