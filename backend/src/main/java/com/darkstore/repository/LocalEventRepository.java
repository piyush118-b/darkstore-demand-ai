package com.darkstore.repository;

import com.darkstore.model.LocalEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LocalEventRepository extends JpaRepository<LocalEvent, Long> {

    List<LocalEvent> findByRegionAndEventDate(String region, LocalDate eventDate);

    /** Find events around a date (for lookahead feature engineering) */
    @Query("""
            SELECT e FROM LocalEvent e
            WHERE e.region = :region
              AND e.eventDate BETWEEN :from AND :to
            ORDER BY e.eventDate ASC
            """)
    List<LocalEvent> findByRegionAndDateRange(@Param("region") String region,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    /**
     * Maximum demand multiplier for a store region on a specific date.
     * Used by the ML feature pipeline to inject the event signal.
     */
    @Query("""
            SELECT MAX(e.demandMultiplier) FROM LocalEvent e
            WHERE e.region = :region
              AND e.eventDate = :date
              AND (e.affectedCategories IS NULL OR e.affectedCategories LIKE CONCAT('%', :category, '%'))
            """)
    Optional<Double> findMaxMultiplierForRegionDateCategory(@Param("region") String region,
                                                            @Param("date") LocalDate date,
                                                            @Param("category") String category);
}
