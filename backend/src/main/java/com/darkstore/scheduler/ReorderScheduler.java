package com.darkstore.scheduler;

import com.darkstore.dto.ReorderEventDto;
import com.darkstore.service.ReorderEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job that runs the Reorder Engine at a configurable interval.
 *
 * <p>The engine sweeps all active dark stores, compares current inventory
 * against ML-predicted demand, and raises {@code ReorderEvent} records
 * for products at STOCKOUT_RISK or LOW_STOCK.
 *
 * <p>Default interval: every 30 minutes ({@code reorder.check-interval-ms=1800000}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReorderScheduler {

    private final ReorderEngineService reorderEngineService;

    /**
     * Full reorder sweep across all active stores.
     * Interval configured via {@code reorder.check-interval-ms} in application.yml.
     */
    @Scheduled(fixedDelayString = "${reorder.check-interval-ms:1800000}")
    public void scheduledReorderCheck() {
        log.info("=== [ReorderScheduler] Starting scheduled reorder sweep at {} ===",
                LocalDateTime.now());

        List<ReorderEventDto> newReorders = reorderEngineService.runFullSweep();

        if (newReorders.isEmpty()) {
            log.info("[ReorderScheduler] No new reorders required. All stock levels healthy.");
        } else {
            log.info("[ReorderScheduler] Sweep complete: {} new reorder events raised.", newReorders.size());
            newReorders.forEach(r ->
                log.info("  → [{}] Store={} Product={} Stock={} Forecast={}",
                        r.getTriggerReason(), r.getStoreId(), r.getProductId(),
                        r.getCurrentStock(), r.getForecastedDemand())
            );
        }
    }
}
