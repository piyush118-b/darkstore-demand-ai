package com.darkstore.controller;

import com.darkstore.dto.ReorderEventDto;
import com.darkstore.service.ReorderEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for reorder event management.
 */
@RestController
@RequestMapping("/api/reorders")
@RequiredArgsConstructor
@Tag(name = "Reorder Events", description = "Manage automatic and manual inventory reorder events")
public class ReorderController {

    private final ReorderEngineService reorderEngineService;

    @Operation(summary = "Get all pending reorder events across all stores")
    @GetMapping("/pending")
    public ResponseEntity<List<ReorderEventDto>> getPendingReorders() {
        return ResponseEntity.ok(reorderEngineService.getPendingReorders());
    }

    @Operation(summary = "Get all reorder events for a specific store")
    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<ReorderEventDto>> getReordersForStore(
            @PathVariable String storeId) {
        return ResponseEntity.ok(reorderEngineService.getReordersForStore(storeId));
    }

    @Operation(summary = "Update the status of a reorder event",
               description = "Valid statuses: APPROVED, DISPATCHED, CANCELLED")
    @PutMapping("/{reorderId}/status")
    public ResponseEntity<ReorderEventDto> updateStatus(
            @PathVariable Long reorderId,
            @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        if (newStatus == null || newStatus.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(reorderEngineService.updateStatus(reorderId, newStatus));
    }

    @Operation(summary = "Manually raise a reorder for a product at a store")
    @PostMapping("/manual")
    public ResponseEntity<ReorderEventDto> manualReorder(@RequestBody Map<String, String> body) {
        String storeId   = body.get("storeId");
        String productId = body.get("productId");
        String notes     = body.getOrDefault("notes", "Manual reorder");
        return ResponseEntity.ok(reorderEngineService.manualReorder(storeId, productId, notes));
    }

    @Operation(summary = "Trigger a full reorder sweep across all active stores",
               description = "Simulates the scheduled job on demand. Useful for demos and testing.")
    @PostMapping("/sweep")
    public ResponseEntity<Map<String, Object>> triggerSweep() {
        List<ReorderEventDto> created = reorderEngineService.runFullSweep();
        return ResponseEntity.ok(Map.of(
                "message", "Reorder sweep complete",
                "newReordersCreated", created.size(),
                "reorders", created
        ));
    }
}
