package com.darkstore.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a quick-commerce dark store (micro-fulfilment centre).
 * Each store handles 10-20 minute deliveries within its local catchment area.
 */
@Entity
@Table(name = "dark_stores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DarkStore {

    @Id
    @Column(name = "id", length = 10)
    private String id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "region", nullable = false, length = 50)
    private String region;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
