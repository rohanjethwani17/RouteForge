package com.routeforge.processing.repository;

import com.routeforge.processing.entity.VehiclePositionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehiclePositionHistoryRepository extends JpaRepository<VehiclePositionHistory, Long> {
    
    Optional<VehiclePositionHistory> findByEventId(String eventId);
    
    @Query("SELECT v FROM VehiclePositionHistory v WHERE v.vehicleId = :vehicleId " +
           "ORDER BY v.tsEpochMs DESC LIMIT 1")
    Optional<VehiclePositionHistory> findLatestByVehicleId(@Param("vehicleId") String vehicleId);
    
    @Query("SELECT v FROM VehiclePositionHistory v WHERE v.routeId = :routeId " +
           "AND v.tsEpochMs >= :sinceTs ORDER BY v.tsEpochMs DESC")
    List<VehiclePositionHistory> findLatestByRouteId(
        @Param("routeId") String routeId,
        @Param("sinceTs") Long sinceTs
    );
}
