package com.darkstore.dto;

import lombok.*;

import java.math.BigDecimal;

/** Request payload sent to the Python ML microservice /predict endpoint */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastRequestDto {

    private String storeId;
    private String productId;
    private String category;
    private String region;

    // ---- Temporal features ----
    private Integer hourOfDay;         // 0-23
    private Integer dayOfWeek;         // 0=Monday … 6=Sunday
    private Boolean isWeekend;
    private Integer month;             // 1-12

    // ---- Price signals ----
    private BigDecimal price;
    private Integer discountPct;
    private BigDecimal competitorPrice;

    // ---- Contextual signals ----
    private String weatherCondition;   // Sunny | Rainy | Cloudy | Snowy
    private Boolean isPromotion;
    private String seasonality;        // Spring | Summer | Autumn | Winter

    // ---- Inventory context ----
    private Integer currentInventoryLevel;

    // ---- Event multiplier from local_events ----
    private Double eventMultiplier;    // 1.0 = no event; >1.0 = demand spike
}
