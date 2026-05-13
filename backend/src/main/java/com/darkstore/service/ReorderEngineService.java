package com.darkstore.service;

import com.darkstore.dto.ForecastResponseDto;
import com.darkstore.dto.ReorderEventDto;
import com.darkstore.model.*;
import com.darkstore.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Reorder Engine — evaluates current stock vs. forecasted demand and raises
 * {@link ReorderEvent} records when risk thresholds are breached.
 *
 * <p>Trigger conditions:
 * <ul>
 *   <li><b>STOCKOUT_RISK</b>: forecasted next-hour demand &gt; current stock × 0.8</li>
 *   <li><b>LOW_STOCK</b>: current stock ≤ product.reorderThreshold</li>
 * </ul>
 *
 * <p>Duplicate suppression: a new reorder is skipped if a PENDING/APPROVED reorder
 * for the same store+product+reason exists within the last 2 hours.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReorderEngineService {

    private final InventorySnapshotRepository snapshotRepository;
    private final ReorderEventRepository reorderRepository;
    private final DarkStoreRepository storeRepository;
    private final ForecastingService forecastingService;

    /** Dedupe window: don't raise same reorder within this many hours */
    private static final int DEDUPE_HOURS = 2;

    // -------------------------------------------------------------------------
    // Called by scheduler and manual trigger
    // -------------------------------------------------------------------------

    /**
     * Sweep all active stores and raise reorders where needed.
     *
     * @return list of newly created reorder events
     */
    @Transactional
    public List<ReorderEventDto> runFullSweep() {
        List<DarkStore> stores = storeRepository.findByIsActiveTrue();
        List<ReorderEventDto> created = new ArrayList<>();

        for (DarkStore store : stores) {
            try {
                created.addAll(sweepStore(store.getId()));
            } catch (Exception ex) {
                log.error("Reorder sweep failed for store {}: {}", store.getId(), ex.getMessage());
            }
        }

        log.info("Reorder sweep complete. {} new reorder events raised.", created.size());
        return created;
    }

    /**
     * Sweep a single store and raise reorders for at-risk products.
     */
    @Transactional
    public List<ReorderEventDto> sweepStore(String storeId) {
        List<InventorySnapshot> latestSnapshots =
                snapshotRepository.findLatestSnapshotPerProductForStore(storeId);

        List<ReorderEventDto> created = new ArrayList<>();

        for (InventorySnapshot snapshot : latestSnapshots) {
            Product product = snapshot.getProduct();
            int currentStock = snapshot.getInventoryLevel();

            // ── LOW_STOCK check ──────────────────────────────────────────────
            if (currentStock <= product.getReorderThreshold()) {
                maybeRaiseReorder(snapshot, "LOW_STOCK", null, created);
            }

            // ── STOCKOUT_RISK check (via ML forecast) ────────────────────────
            try {
                Optional<ForecastResponseDto> latestForecast =
                        forecastingService.getLatestForecast(storeId, product.getId());

                int forecastedDemand = latestForecast
                        .map(ForecastResponseDto::getPredictedUnits)
                        .orElse(0);

                if (forecastedDemand > 0 && forecastedDemand > currentStock * 0.8) {
                    maybeRaiseReorder(snapshot, "STOCKOUT_RISK", forecastedDemand, created);
                }
            } catch (Exception ex) {
                log.debug("Skipping STOCKOUT_RISK check for product {}: {}", product.getId(), ex.getMessage());
            }
        }

        return created;
    }

    /**
     * Manually trigger a reorder for a specific store + product.
     */
    @Transactional
    public ReorderEventDto manualReorder(String storeId, String productId, String notes) {
        InventorySnapshot snapshot = snapshotRepository
                .findTopByStore_IdAndProduct_IdOrderBySnapshotTimeDesc(storeId, productId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No snapshot found for store=" + storeId + " product=" + productId));

        ReorderEvent event = buildReorderEvent(snapshot, "MANUAL", null);
        event.setNotes(notes);
        ReorderEvent saved = reorderRepository.save(event);
        log.info("Manual reorder raised: store={} product={}", storeId, productId);
        return toDto(saved);
    }

    /**
     * Update the status of a reorder event (approve, dispatch, cancel).
     */
    @Transactional
    public ReorderEventDto updateStatus(Long reorderId, String newStatus) {
        ReorderEvent event = reorderRepository.findById(reorderId)
                .orElseThrow(() -> new NoSuchElementException("Reorder not found: " + reorderId));

        event.setStatus(newStatus.toUpperCase());
        if ("APPROVED".equals(event.getStatus()))    event.setApprovedAt(LocalDateTime.now());
        if ("DISPATCHED".equals(event.getStatus()))  event.setDispatchedAt(LocalDateTime.now());

        return toDto(reorderRepository.save(event));
    }

    /** Return all pending reorder events across all stores */
    public List<ReorderEventDto> getPendingReorders() {
        return reorderRepository.findByStatusOrderByTriggeredAtDesc("PENDING")
                .stream().map(this::toDto).toList();
    }

    /** Return all reorder events for a store */
    public List<ReorderEventDto> getReordersForStore(String storeId) {
        return reorderRepository.findByStore_IdOrderByTriggeredAtDesc(storeId)
                .stream().map(this::toDto).toList();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void maybeRaiseReorder(InventorySnapshot snapshot, String reason,
                                   Integer forecastedDemand, List<ReorderEventDto> created) {
        String storeId   = snapshot.getStore().getId();
        String productId = snapshot.getProduct().getId();
        LocalDateTime dedupeWindow = LocalDateTime.now().minusHours(DEDUPE_HOURS);

        if (reorderRepository.existsRecentReorder(storeId, productId, reason, dedupeWindow)) {
            log.debug("Skipping duplicate reorder: store={} product={} reason={}", storeId, productId, reason);
            return;
        }

        ReorderEvent event = buildReorderEvent(snapshot, reason, forecastedDemand);
        ReorderEvent saved = reorderRepository.save(event);
        log.info("Reorder raised [{}]: store={} product={} stock={} forecast={}",
                reason, storeId, productId, snapshot.getInventoryLevel(), forecastedDemand);
        created.add(toDto(saved));
    }

    private ReorderEvent buildReorderEvent(InventorySnapshot snapshot, String reason,
                                           Integer forecastedDemand) {
        return ReorderEvent.builder()
                .store(snapshot.getStore())
                .product(snapshot.getProduct())
                .triggeredAt(LocalDateTime.now())
                .triggerReason(reason)
                .currentStock(snapshot.getInventoryLevel())
                .forecastedDemand(forecastedDemand)
                .reorderQuantity(snapshot.getProduct().getReorderQuantity())
                .status("PENDING")
                .build();
    }

    private ReorderEventDto toDto(ReorderEvent e) {
        return ReorderEventDto.builder()
                .id(e.getId())
                .storeId(e.getStore().getId())
                .storeName(e.getStore().getName())
                .productId(e.getProduct().getId())
                .productName(e.getProduct().getName())
                .category(e.getProduct().getCategory())
                .triggeredAt(e.getTriggeredAt())
                .triggerReason(e.getTriggerReason())
                .currentStock(e.getCurrentStock())
                .forecastedDemand(e.getForecastedDemand())
                .reorderQuantity(e.getReorderQuantity())
                .status(e.getStatus())
                .approvedAt(e.getApprovedAt())
                .dispatchedAt(e.getDispatchedAt())
                .notes(e.getNotes())
                .build();
    }
}
