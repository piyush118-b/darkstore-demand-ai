package com.darkstore.service;

import com.darkstore.dto.ForecastRequestDto;
import com.darkstore.dto.ForecastResponseDto;
import com.darkstore.model.*;
import com.darkstore.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Orchestrates demand forecast generation by calling the Python ML microservice.
 *
 * <p>Flow:
 * <ol>
 *   <li>Build a {@link ForecastRequestDto} from current inventory + context signals</li>
 *   <li>Inject local event multiplier from {@link LocalEventRepository}</li>
 *   <li>POST to ML microservice {@code /predict}</li>
 *   <li>Persist {@link DemandForecast} in PostgreSQL with SHAP explanations</li>
 *   <li>Classify stock risk level and return {@link ForecastResponseDto}</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ForecastingService {

    private final RestTemplate restTemplate;
    private final DemandForecastRepository forecastRepository;
    private final InventorySnapshotRepository snapshotRepository;
    private final DarkStoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final LocalEventRepository eventRepository;

    @Value("${ml-service.base-url}")
    private String mlServiceBaseUrl;

    @Value("${ml-service.predict-endpoint}")
    private String predictEndpoint;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generate a demand forecast for a specific store + product for the next hour.
     *
     * @param storeId   dark store identifier
     * @param productId product identifier
     * @return forecast DTO with SHAP explanations and stock risk level
     */
    public ForecastResponseDto forecastForProduct(String storeId, String productId) {
        DarkStore store   = storeRepository.findById(storeId)
                .orElseThrow(() -> new NoSuchElementException("Store not found: " + storeId));
        Product   product = productRepository.findById(productId)
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + productId));

        InventorySnapshot latest = snapshotRepository
                .findTopByStore_IdAndProduct_IdOrderBySnapshotTimeDesc(storeId, productId)
                .orElse(null);

        ForecastRequestDto request = buildRequest(store, product, latest);
        ForecastResponseDto mlResponse = callMlService(request);

        // Persist result
        DemandForecast forecast = persistForecast(store, product, mlResponse);

        // Enrich with stock context
        int currentStock = (latest != null) ? latest.getInventoryLevel() : 0;
        return enrichWithRisk(mlResponse, forecast.getId(), currentStock, product);
    }

    /**
     * Generate forecasts for all active products at a store.
     *
     * @param storeId dark store identifier
     * @return list of forecast DTOs
     */
    public List<ForecastResponseDto> forecastForStore(String storeId) {
        DarkStore store = storeRepository.findById(storeId)
                .orElseThrow(() -> new NoSuchElementException("Store not found: " + storeId));

        List<InventorySnapshot> latestSnapshots =
                snapshotRepository.findLatestSnapshotPerProductForStore(storeId);

        List<ForecastResponseDto> results = new ArrayList<>();
        for (InventorySnapshot snapshot : latestSnapshots) {
            try {
                ForecastRequestDto request = buildRequest(store, snapshot.getProduct(), snapshot);
                ForecastResponseDto mlResponse = callMlService(request);
                DemandForecast forecast = persistForecast(store, snapshot.getProduct(), mlResponse);
                results.add(enrichWithRisk(mlResponse, forecast.getId(),
                        snapshot.getInventoryLevel(), snapshot.getProduct()));
            } catch (Exception ex) {
                log.error("Forecast failed for store={} product={}: {}",
                        storeId, snapshot.getProduct().getId(), ex.getMessage());
            }
        }
        return results;
    }

    /**
     * Retrieve the most recent stored forecast for a product at a store.
     * Does NOT call the ML service — use for read-heavy dashboard endpoints.
     */
    public Optional<ForecastResponseDto> getLatestForecast(String storeId, String productId) {
        return forecastRepository
                .findTopByStore_IdAndProduct_IdOrderByForecastTimeDesc(storeId, productId)
                .map(f -> {
                    int currentStock = snapshotRepository
                            .findTopByStore_IdAndProduct_IdOrderBySnapshotTimeDesc(storeId, productId)
                            .map(InventorySnapshot::getInventoryLevel)
                            .orElse(0);
                    Product product = f.getProduct();
                    ForecastResponseDto dto = toDto(f);
                    dto.setCurrentStock(currentStock);
                    dto.setRiskLevel(classifyRisk(currentStock, f.getPredictedUnits(), product));
                    return dto;
                });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private ForecastRequestDto buildRequest(DarkStore store, Product product,
                                            InventorySnapshot latest) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        // Resolve event multiplier for this region + category today
        double eventMultiplier = eventRepository
                .findMaxMultiplierForRegionDateCategory(store.getRegion(), today, product.getCategory())
                .orElse(1.0);

        return ForecastRequestDto.builder()
                .storeId(store.getId())
                .productId(product.getId())
                .category(product.getCategory())
                .region(store.getRegion())
                .hourOfDay(now.getHour())
                .dayOfWeek(now.getDayOfWeek().getValue() - 1)   // 0=Mon, 6=Sun
                .isWeekend(now.getDayOfWeek().getValue() >= 6)
                .month(now.getMonthValue())
                .price(latest != null ? latest.getPrice() : product.getUnitPrice())
                .discountPct(latest != null ? latest.getDiscountPct() : 0)
                .competitorPrice(latest != null ? latest.getCompetitorPrice() : null)
                .weatherCondition(latest != null ? latest.getWeatherCondition() : "Sunny")
                .isPromotion(latest != null && Boolean.TRUE.equals(latest.getIsPromotion()))
                .seasonality(latest != null ? latest.getSeasonality() : currentSeason(now.getMonthValue()))
                .currentInventoryLevel(latest != null ? latest.getInventoryLevel() : 0)
                .eventMultiplier(eventMultiplier)
                .build();
    }

    /**
     * Call the Python ML microservice. Falls back to a heuristic estimate
     * if the service is unavailable (graceful degradation for portfolio demo).
     */
    private ForecastResponseDto callMlService(ForecastRequestDto request) {
        String url = mlServiceBaseUrl + predictEndpoint;
        try {
            ForecastResponseDto response = restTemplate.postForObject(url, request, ForecastResponseDto.class);
            if (response == null) throw new RestClientException("Null response from ML service");
            return response;
        } catch (RestClientException ex) {
            log.warn("ML service unavailable ({}). Using heuristic fallback.", ex.getMessage());
            return heuristicForecast(request);
        }
    }

    /**
     * Simple rule-based fallback when ML service is offline.
     * Based on hour-of-day demand distribution from EDA insights.
     */
    private ForecastResponseDto heuristicForecast(ForecastRequestDto req) {
        double hourMultiplier = getHourMultiplier(req.getHourOfDay());
        double baseUnits = 50.0;
        if (Boolean.TRUE.equals(req.getIsPromotion()))  baseUnits *= 1.4;
        if (Boolean.TRUE.equals(req.getIsWeekend()))    baseUnits *= 1.2;
        baseUnits *= req.getEventMultiplier() != null ? req.getEventMultiplier() : 1.0;
        int predicted = (int) Math.max(1, Math.round(baseUnits * hourMultiplier));

        return ForecastResponseDto.builder()
                .storeId(req.getStoreId())
                .productId(req.getProductId())
                .forecastTime(LocalDateTime.now().plusHours(1))
                .predictedUnits(predicted)
                .confidenceScore(BigDecimal.valueOf(0.55))
                .modelVersion("heuristic-fallback")
                .shapExplanation(Map.of(
                        "hour_of_day", hourMultiplier - 1.0,
                        "is_promotion", Boolean.TRUE.equals(req.getIsPromotion()) ? 0.4 : 0.0,
                        "event_multiplier", req.getEventMultiplier() - 1.0))
                .build();
    }

    private DemandForecast persistForecast(DarkStore store, Product product,
                                           ForecastResponseDto dto) {
        DemandForecast forecast = DemandForecast.builder()
                .store(store)
                .product(product)
                .forecastTime(dto.getForecastTime() != null
                        ? dto.getForecastTime() : LocalDateTime.now().plusHours(1))
                .predictedUnits(dto.getPredictedUnits())
                .confidenceScore(dto.getConfidenceScore())
                .modelVersion(dto.getModelVersion() != null ? dto.getModelVersion() : "1.0")
                .shapExplanation(dto.getShapExplanation())
                .build();
        return forecastRepository.save(forecast);
    }

    private ForecastResponseDto enrichWithRisk(ForecastResponseDto dto, Long forecastId,
                                               int currentStock, Product product) {
        dto.setCurrentStock(currentStock);
        dto.setRiskLevel(classifyRisk(currentStock, dto.getPredictedUnits(), product));
        dto.setProductName(product.getName());
        dto.setCategory(product.getCategory());
        return dto;
    }

    private ForecastResponseDto.StockRiskLevel classifyRisk(int stock, int predicted, Product product) {
        if (stock < predicted)             return ForecastResponseDto.StockRiskLevel.CRITICAL;
        if (stock < predicted * 2)         return ForecastResponseDto.StockRiskLevel.WARNING;
        return ForecastResponseDto.StockRiskLevel.SAFE;
    }

    private ForecastResponseDto toDto(DemandForecast f) {
        return ForecastResponseDto.builder()
                .storeId(f.getStore().getId())
                .productId(f.getProduct().getId())
                .productName(f.getProduct().getName())
                .category(f.getProduct().getCategory())
                .forecastTime(f.getForecastTime())
                .predictedUnits(f.getPredictedUnits())
                .confidenceScore(f.getConfidenceScore())
                .modelVersion(f.getModelVersion())
                .shapExplanation(f.getShapExplanation())
                .build();
    }

    // Peak-hour demand distribution derived from EDA
    private double getHourMultiplier(int hour) {
        if (hour >= 7  && hour <= 9)  return 1.3;   // morning rush
        if (hour >= 12 && hour <= 14) return 1.4;   // lunch peak
        if (hour >= 17 && hour <= 20) return 1.6;   // evening peak
        if (hour >= 0  && hour <= 5)  return 0.3;   // night lull
        return 1.0;
    }

    private String currentSeason(int month) {
        if (month >= 3 && month <= 5)  return "Spring";
        if (month >= 6 && month <= 8)  return "Summer";
        if (month >= 9 && month <= 11) return "Autumn";
        return "Winter";
    }
}
