package com.darkstore.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An automatic or manual reorder event raised by the Reorder Engine.
 *
 * <p>The engine compares current stock against the next-hour forecast and
 * raises a STOCKOUT_RISK event when: {@code forecasted_demand > current_stock * 0.8}.
 * A LOW_STOCK event fires when: {@code current_stock <= product.reorder_threshold}.
 */
@Entity
@Table(name = "reorder_events",
        indexes = {
            @Index(name = "idx_reorder_store",  columnList = "store_id, triggered_at DESC"),
            @Index(name = "idx_reorder_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReorderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private DarkStore store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    /**
     * What caused this reorder.
     * Values: STOCKOUT_RISK | LOW_STOCK | SCHEDULED | MANUAL
     */
    @Column(name = "trigger_reason", nullable = false, length = 50)
    private String triggerReason;

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    /** Demand forecasted for the next 2 hours from ML microservice */
    @Column(name = "forecasted_demand")
    private Integer forecastedDemand;

    @Column(name = "reorder_quantity", nullable = false)
    private Integer reorderQuantity;

    /**
     * Lifecycle status.
     * Values: PENDING | APPROVED | DISPATCHED | CANCELLED
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (triggeredAt == null) triggeredAt = LocalDateTime.now();
    }
}
