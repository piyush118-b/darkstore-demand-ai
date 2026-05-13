package com.darkstore.repository;

import com.darkstore.model.DemandForecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DemandForecastRepository extends JpaRepository<DemandForecast, Long> {

    /** Latest forecast for a product at a store */
    Optional<DemandForecast> findTopByStore_IdAndProduct_IdOrderByForecastTimeDesc(
            String storeId, String productId);

    /** All forecasts for a store over a time window */
    List<DemandForecast> findByStore_IdAndForecastTimeBetweenOrderByForecastTimeAsc(
            String storeId, LocalDateTime from, LocalDateTime to);

    /** Forecasts created in the last N minutes (freshness check) */
    @Query("""
            SELECT f FROM DemandForecast f
            WHERE f.store.id = :storeId
              AND f.createdAt >= :since
            ORDER BY f.forecastTime DESC
            """)
    List<DemandForecast> findRecentByStore(@Param("storeId") String storeId,
                                           @Param("since") LocalDateTime since);

    /** Total reorder events triggered today (dashboard KPI) */
    @Query("SELECT COUNT(f) FROM DemandForecast f WHERE f.createdAt >= :startOfDay")
    long countForecastsToday(@Param("startOfDay") LocalDateTime startOfDay);
}
