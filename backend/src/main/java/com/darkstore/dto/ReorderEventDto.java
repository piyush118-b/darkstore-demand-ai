package com.darkstore.dto;

import lombok.*;

import java.time.LocalDateTime;

/** Response DTO for reorder event operations */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReorderEventDto {

    private Long id;
    private String storeId;
    private String storeName;
    private String productId;
    private String productName;
    private String category;

    private LocalDateTime triggeredAt;

    /**
     * Trigger reason.
     * Values: STOCKOUT_RISK | LOW_STOCK | SCHEDULED | MANUAL
     */
    private String triggerReason;

    private Integer currentStock;
    private Integer forecastedDemand;
    private Integer reorderQuantity;

    /**
     * Lifecycle status.
     * Values: PENDING | APPROVED | DISPATCHED | CANCELLED
     */
    private String status;

    private LocalDateTime approvedAt;
    private LocalDateTime dispatchedAt;
    private String notes;
}
