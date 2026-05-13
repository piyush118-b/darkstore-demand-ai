package com.darkstore.dto;

import lombok.*;

import java.util.List;

/** Dashboard summary KPIs */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDto {

    private long activeStores;
    private long totalProducts;

    /** Reorders triggered in the last 24 hours */
    private long reordersTodayCount;

    /** Currently pending reorders awaiting approval */
    private long pendingReordersCount;

    /** Products at CRITICAL stock risk right now */
    private long criticalStockAlerts;

    /** Products at WARNING stock risk right now */
    private long warningStockAlerts;

    /** ML forecasts generated in the last 24 hours */
    private long forecastsGeneratedToday;

    /** Top 5 critical stock alert details */
    private List<StockAlertDto> topAlerts;
}
