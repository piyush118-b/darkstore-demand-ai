package com.darkstore.controller;

import com.darkstore.dto.ForecastResponseDto;
import com.darkstore.service.ForecastingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for demand forecast generation and retrieval.
 */
@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
@Tag(name = "Demand Forecast", description = "ML-powered demand predictions for dark store products")
public class ForecastController {

    private final ForecastingService forecastingService;

    @Operation(summary = "Trigger forecasts for all products at a store",
               description = "Calls the XGBoost ML microservice for each product at the specified store " +
                             "and persists predictions with SHAP explanations.")
    @PostMapping("/store/{storeId}")
    public ResponseEntity<List<ForecastResponseDto>> forecastForStore(
            @PathVariable String storeId) {
        return ResponseEntity.ok(forecastingService.forecastForStore(storeId));
    }

    @Operation(summary = "Trigger forecast for a single product at a store")
    @PostMapping("/store/{storeId}/product/{productId}")
    public ResponseEntity<ForecastResponseDto> forecastForProduct(
            @PathVariable String storeId,
            @PathVariable String productId) {
        return ResponseEntity.ok(forecastingService.forecastForProduct(storeId, productId));
    }

    @Operation(summary = "Get the latest stored forecast for a product at a store",
               description = "Returns the most recent forecast without triggering the ML service.")
    @GetMapping("/store/{storeId}/product/{productId}/latest")
    public ResponseEntity<ForecastResponseDto> getLatestForecast(
            @PathVariable String storeId,
            @PathVariable String productId) {
        return forecastingService.getLatestForecast(storeId, productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
