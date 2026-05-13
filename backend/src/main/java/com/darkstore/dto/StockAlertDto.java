package com.darkstore.dto;

import lombok.*;

/** A stock risk alert for a product at a store */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAlertDto {

    private String storeId;
    private String storeName;
    private String productId;
    private String productName;
    private String category;
    private Integer currentStock;
    private Integer reorderThreshold;
    private Integer forecastedDemandNextHour;

    /**
     * Risk classification.
     * Values: CRITICAL | WARNING | SAFE
     */
    private String riskLevel;

    /** Human-readable explanation for the alert */
    private String reason;
}
