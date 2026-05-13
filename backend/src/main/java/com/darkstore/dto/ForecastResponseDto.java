package com.darkstore.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** Response DTO returned by ForecastController and the ML microservice wrapper */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastResponseDto {

    private String storeId;
    private String productId;
    private String productName;
    private String category;

    /** The hour being predicted */
    private LocalDateTime forecastTime;

    /** Predicted units to be sold in this hour */
    private Integer predictedUnits;

    /** Model confidence score in [0, 1] */
    private BigDecimal confidenceScore;

    private String modelVersion;

    /**
     * SHAP feature contributions (top features).
     * Positive = increases predicted demand; negative = decreases it.
     * Example: {"price": -0.42, "weather_rainy": 0.31, "hour_of_day": 0.18}
     */
    private Map<String, Double> shapExplanation;

    /** Current inventory level at the time of forecast */
    private Integer currentStock;

    /** Derived risk level based on stock vs forecast */
    private StockRiskLevel riskLevel;

    public enum StockRiskLevel {
        SAFE,        // stock > 2× predicted demand
        WARNING,     // stock between 1× and 2× predicted demand
        CRITICAL     // stock < 1× predicted demand
    }
}
