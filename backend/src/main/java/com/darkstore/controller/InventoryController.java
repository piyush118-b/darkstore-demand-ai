package com.darkstore.controller;

import com.darkstore.model.InventorySnapshot;
import com.darkstore.repository.InventorySnapshotRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * REST endpoints for inventory snapshot queries.
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory snapshot queries and stock history")
public class InventoryController {

    private final InventorySnapshotRepository snapshotRepository;

    @Operation(summary = "Get the latest stock snapshot for a product at a store")
    @GetMapping("/store/{storeId}/product/{productId}/latest")
    public ResponseEntity<InventorySnapshot> getLatest(
            @PathVariable String storeId,
            @PathVariable String productId) {
        Optional<InventorySnapshot> snapshot =
                snapshotRepository.findTopByStore_IdAndProduct_IdOrderBySnapshotTimeDesc(storeId, productId);
        return snapshot.map(ResponseEntity::ok)
                       .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get hourly stock history for a product at a store within a time window")
    @GetMapping("/store/{storeId}/product/{productId}/history")
    public ResponseEntity<List<InventorySnapshot>> getHistory(
            @PathVariable String storeId,
            @PathVariable String productId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(
                snapshotRepository.findByStore_IdAndProduct_IdAndSnapshotTimeBetweenOrderBySnapshotTimeAsc(
                        storeId, productId, from, to));
    }

    @Operation(summary = "Get all products with low stock at a store",
               description = "Returns snapshots where inventory_level ≤ reorder threshold. " +
                             "Uses threshold=50 as default if product-level threshold is not resolved.")
    @GetMapping("/store/{storeId}/low-stock")
    public ResponseEntity<List<InventorySnapshot>> getLowStock(
            @PathVariable String storeId,
            @RequestParam(defaultValue = "50") int threshold) {
        return ResponseEntity.ok(
                snapshotRepository.findLowStockByStore(storeId, threshold));
    }

    @Operation(summary = "Get all latest snapshots for a store (full stock view)")
    @GetMapping("/store/{storeId}/current")
    public ResponseEntity<List<InventorySnapshot>> getCurrentStockForStore(
            @PathVariable String storeId) {
        return ResponseEntity.ok(
                snapshotRepository.findLatestSnapshotPerProductForStore(storeId));
    }
}
