package com.darkstore.controller;

import com.darkstore.dto.DashboardSummaryDto;
import com.darkstore.dto.StockAlertDto;
import com.darkstore.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for the operations dashboard.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Operational KPIs and stock risk alerts")
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Get dashboard summary KPIs",
               description = "Returns active stores, total products, reorders today, " +
                             "pending reorders, forecast counts, and top stock alerts.")
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryDto> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    @Operation(summary = "Get current stock risk alerts",
               description = "Returns products at CRITICAL or WARNING stock levels across all stores.")
    @GetMapping("/alerts")
    public ResponseEntity<List<StockAlertDto>> getAlerts() {
        return ResponseEntity.ok(dashboardService.getAlerts());
    }
}
