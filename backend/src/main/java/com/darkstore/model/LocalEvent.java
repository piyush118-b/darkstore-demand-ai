package com.darkstore.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A local event (festival, concert, sports match, sale) that is expected
 * to amplify consumer demand in a specific region on a given date.
 *
 * <p>The ML feature pipeline reads the {@code demand_multiplier} for the
 * store's region on the forecast date and applies it as an input feature.
 */
@Entity
@Table(name = "local_events",
        indexes = {
            @Index(name = "idx_event_region_date", columnList = "region, event_date")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_name", nullable = false, length = 100)
    private String eventName;

    @Column(name = "region", nullable = false, length = 50)
    private String region;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    /**
     * Type of event.
     * Values: HOLIDAY | SALE | SPORTS | CONCERT | FESTIVAL
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /**
     * Demand multiplier relative to baseline.
     * e.g. 1.5 = 50% demand surge; 1.0 = no effect
     */
    @Column(name = "demand_multiplier", nullable = false, precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal demandMultiplier = BigDecimal.ONE;

    /**
     * Comma-separated category names this event affects.
     * NULL means all categories are affected.
     */
    @Column(name = "affected_categories", length = 200)
    private String affectedCategories;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
