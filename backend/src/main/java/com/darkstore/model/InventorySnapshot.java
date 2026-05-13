package com.darkstore.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Hourly inventory snapshot for a product at a specific dark store.
 *
 * <p>The original dataset is daily-granular; during ingestion we simulate hourly
 * records by distributing daily sales across peak/off-peak hours using a demand
 * distribution curve (see {@link com.darkstore.service.DataIngestionService}).
 */
@Entity
@Table(name = "inventory_snapshots",
        indexes = {
            @Index(name = "idx_snapshot_store_time",   columnList = "store_id, snapshot_time DESC"),
            @Index(name = "idx_snapshot_product_time", columnList = "product_id, snapshot_time DESC"),
            @Index(name = "idx_snapshot_time",         columnList = "snapshot_time DESC")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventorySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private DarkStore store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "snapshot_time", nullable = false)
    private LocalDateTime snapshotTime;

    @Column(name = "inventory_level", nullable = false)
    private Integer inventoryLevel;

    @Column(name = "units_sold", nullable = false)
    @Builder.Default
    private Integer unitsSold = 0;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "discount_pct")
    @Builder.Default
    private Integer discountPct = 0;

    @Column(name = "weather_condition", length = 20)
    private String weatherCondition;

    @Column(name = "is_promotion", nullable = false)
    @Builder.Default
    private Boolean isPromotion = false;

    @Column(name = "competitor_price", precision = 10, scale = 2)
    private BigDecimal competitorPrice;

    @Column(name = "seasonality", length = 20)
    private String seasonality;

    @Column(name = "hour_of_day")
    private Integer hourOfDay;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "is_weekend", nullable = false)
    @Builder.Default
    private Boolean isWeekend = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
