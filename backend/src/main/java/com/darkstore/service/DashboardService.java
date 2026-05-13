package com.darkstore.service;

import com.darkstore.dto.DashboardSummaryDto;
import com.darkstore.dto.StockAlertDto;
import com.darkstore.model.InventorySnapshot;
import com.darkstore.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles dashboard KPI data by querying across all repositories.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final DarkStoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final ReorderEventRepository reorderRepository;
    private final DemandForecastRepository forecastRepository;
    private final InventorySnapshotRepository snapshotRepository;

    /**
     * Returns a summary of key business metrics for the dashboard.
     */
    public DashboardSummaryDto getSummary() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        long activeStores   = storeRepository.findByIsActiveTrue().size();
        long totalProducts  = productRepository.findByIsActiveTrue().size();
        long reordersToday  = reorderRepository.countReordersToday(startOfDay);
        long pendingReorders = reorderRepository.countByStatus("PENDING");
        long forecastsToday = forecastRepository.countForecastsToday(startOfDay);

        List<StockAlertDto> topAlerts = buildTopAlerts();
        long criticalCount = topAlerts.stream().filter(a -> "CRITICAL".equals(a.getRiskLevel())).count();
        long warningCount  = topAlerts.stream().filter(a -> "WARNING".equals(a.getRiskLevel())).count();

        return DashboardSummaryDto.builder()
                .activeStores(activeStores)
                .totalProducts(totalProducts)
                .reordersTodayCount(reordersToday)
                .pendingReordersCount(pendingReorders)
                .forecastsGeneratedToday(forecastsToday)
                .criticalStockAlerts(criticalCount)
                .warningStockAlerts(warningCount)
                .topAlerts(topAlerts)
                .build();
    }

    /**
     * Returns current stock alerts across all active stores.
     * A product is flagged if its inventory level is at or below
     * 2× the reorder threshold (WARNING) or at/below 1× (CRITICAL).
     */
    public List<StockAlertDto> getAlerts() {
        return buildTopAlerts();
    }

    // -------------------------------------------------------------------------

    private List<StockAlertDto> buildTopAlerts() {
        List<StockAlertDto> alerts = new ArrayList<>();

        storeRepository.findByIsActiveTrue().forEach(store -> {
            List<InventorySnapshot> snapshots =
                    snapshotRepository.findLatestSnapshotPerProductForStore(store.getId());

            for (InventorySnapshot snap : snapshots) {
                int stock     = snap.getInventoryLevel();
                int threshold = snap.getProduct().getReorderThreshold();

                String riskLevel;
                String reason;

                if (stock <= threshold) {
                    riskLevel = "CRITICAL";
                    reason    = String.format("Stock (%d) at or below reorder threshold (%d)", stock, threshold);
                } else if (stock <= threshold * 2) {
                    riskLevel = "WARNING";
                    reason    = String.format("Stock (%d) below 2× reorder threshold (%d)", stock, threshold * 2);
                } else {
                    continue; // safe — skip
                }

                alerts.add(StockAlertDto.builder()
                        .storeId(store.getId())
                        .storeName(store.getName())
                        .productId(snap.getProduct().getId())
                        .productName(snap.getProduct().getName())
                        .category(snap.getProduct().getCategory())
                        .currentStock(stock)
                        .reorderThreshold(threshold)
                        .riskLevel(riskLevel)
                        .reason(reason)
                        .build());
            }
        });

        // Sort: CRITICAL first, then WARNING
        alerts.sort((a, b) -> {
            int ra = "CRITICAL".equals(a.getRiskLevel()) ? 0 : 1;
            int rb = "CRITICAL".equals(b.getRiskLevel()) ? 0 : 1;
            return Integer.compare(ra, rb);
        });

        return alerts.size() > 20 ? alerts.subList(0, 20) : alerts;
    }
}
