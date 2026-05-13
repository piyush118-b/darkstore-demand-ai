package com.darkstore.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * A demand forecast produced by the XGBoost ML microservice.
 * Includes per-feature SHAP explanations to make predictions accountable.
 */
@Entity
@Table(name = "demand_forecasts",
        indexes = {
            @Index(name = "idx_forecast_store_time",   columnList = "store_id, forecast_time DESC"),
            @Index(name = "idx_forecast_product_time", columnList = "product_id, forecast_time DESC")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private DarkStore store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** The hour for which this demand is predicted */
    @Column(name = "forecast_time", nullable = false)
    private LocalDateTime forecastTime;

    @Column(name = "predicted_units", nullable = false)
    private Integer predictedUnits;

    /** Model confidence in [0, 1] */
    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "model_version", length = 20, nullable = false)
    @Builder.Default
    private String modelVersion = "1.0";

    /**
     * SHAP feature explanations stored as JSONB.
     * Example: {"price": -0.42, "weather": 0.31, "hour_of_day": 0.18}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shap_explanation", columnDefinition = "jsonb")
    private Map<String, Double> shapExplanation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
