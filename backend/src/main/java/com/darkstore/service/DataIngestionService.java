package com.darkstore.service;

import com.darkstore.model.*;
import com.darkstore.repository.*;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Loads {@code retail_store_inventory.csv} into PostgreSQL at startup.
 *
 * <p><b>Hourly simulation strategy:</b>
 * The CSV is daily-granular. We simulate hourly demand using a demand distribution
 * curve derived from quick-commerce EDA:
 * <ul>
 *   <li>07:00–09:00 → morning rush (15% of daily demand)</li>
 *   <li>12:00–14:00 → lunch peak (20% of daily demand)</li>
 *   <li>17:00–20:00 → evening peak (30% of daily demand)</li>
 *   <li>remaining hours → flat distribution of remainder</li>
 * </ul>
 *
 * <p>Only runs when {@code ingestion.enabled=true} and the snapshots table is empty.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataIngestionService {

    private final InventorySnapshotRepository snapshotRepository;
    private final DarkStoreRepository storeRepository;
    private final ProductRepository productRepository;

    @Value("${ingestion.enabled:true}")
    private boolean ingestionEnabled;

    @Value("${ingestion.max-rows:5000}")
    private int maxRows;

    @Value("classpath:data/retail_store_inventory.csv")
    private Resource csvResource;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Hourly demand distribution weights (index = hour 0-23)
    private static final double[] HOUR_WEIGHTS = {
        0.005, 0.003, 0.002, 0.002, 0.003, 0.008,   // 00-05 (night)
        0.015, 0.050, 0.060, 0.040, 0.030, 0.030,   // 06-11 (morning)
        0.060, 0.070, 0.050, 0.040, 0.035, 0.080,   // 12-17 (afternoon)
        0.090, 0.080, 0.070, 0.050, 0.030, 0.010    // 18-23 (evening)
    };

    @PostConstruct
    @Transactional
    public void ingest() {
        if (!ingestionEnabled) {
            log.info("Data ingestion disabled via config.");
            return;
        }
        if (snapshotRepository.count() > 0) {
            log.info("Snapshots table already populated ({} rows). Skipping ingestion.",
                    snapshotRepository.count());
            return;
        }

        log.info("Starting CSV ingestion (max {} rows)...", maxRows);
        int loaded = 0;

        try (CSVReader reader = new CSVReader(new InputStreamReader(csvResource.getInputStream()))) {
            reader.readNext(); // skip header

            String[] line;
            List<InventorySnapshot> batch = new ArrayList<>();

            while ((line = reader.readNext()) != null && loaded < maxRows) {
                try {
                    List<InventorySnapshot> hourlySnapshots = parseLine(line);
                    batch.addAll(hourlySnapshots);
                    loaded++;

                    if (batch.size() >= 500) {
                        snapshotRepository.saveAll(batch);
                        batch.clear();
                        log.debug("Ingested {} CSV rows...", loaded);
                    }
                } catch (Exception e) {
                    log.warn("Skipping malformed CSV row: {} | Error: {}", Arrays.toString(line), e.getMessage());
                }
            }

            if (!batch.isEmpty()) snapshotRepository.saveAll(batch);
            log.info("Ingestion complete: {} daily CSV rows → {} hourly snapshots",
                    loaded, loaded * 4); // ~4 peak hours per day

        } catch (IOException | CsvValidationException e) {
            log.error("CSV ingestion failed: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Convert one daily CSV row into 4 representative hourly snapshots
     * (one per major demand period: morning, lunch, evening, night).
     */
    private List<InventorySnapshot> parseLine(String[] cols) {
        // CSV columns:
        // 0:Date, 1:StoreID, 2:ProductID, 3:Category, 4:Region,
        // 5:InventoryLevel, 6:UnitsSold, 7:UnitsOrdered, 8:DemandForecast,
        // 9:Price, 10:Discount, 11:WeatherCondition, 12:Holiday/Promotion,
        // 13:CompetitorPricing, 14:Seasonality

        String storeId   = cols[1].trim();
        String productId = cols[2].trim();

        // Resolve or create store
        DarkStore store = storeRepository.findById(storeId).orElseGet(() -> {
            DarkStore s = DarkStore.builder()
                    .id(storeId)
                    .name("Dark Store " + storeId)
                    .region(cols[4].trim())
                    .isActive(true)
                    .build();
            return storeRepository.save(s);
        });

        // Resolve or create product
        Product product = productRepository.findById(productId).orElseGet(() -> {
            BigDecimal price = parseBigDecimal(cols[9]);
            Product p = Product.builder()
                    .id(productId)
                    .name("Product " + productId)
                    .category(cols[3].trim())
                    .unitPrice(price)
                    .reorderThreshold(50)
                    .reorderQuantity(200)
                    .isActive(true)
                    .build();
            return productRepository.save(p);
        });

        LocalDate date          = LocalDate.parse(cols[0].trim(), DATE_FMT);
        int dailyUnitsSold      = parseInt(cols[6]);
        int inventoryLevel      = parseInt(cols[5]);
        BigDecimal price        = parseBigDecimal(cols[9]);
        int discount            = parseInt(cols[10]);
        String weather          = cols[11].trim();
        boolean isPromo         = "1".equals(cols[12].trim());
        BigDecimal compPrice    = parseBigDecimal(cols[13]);
        String seasonality      = cols[14].trim();

        // Generate 4 representative hourly snapshots: 8am, 13pm, 18pm, 22pm
        int[] hours = {8, 13, 18, 22};
        List<InventorySnapshot> snapshots = new ArrayList<>();

        for (int hour : hours) {
            double weight       = HOUR_WEIGHTS[hour];
            int hourlySold      = (int) Math.round(dailyUnitsSold * weight / sumPeakWeights(hours));
            int hourlyInventory = Math.max(0, inventoryLevel - hourlySold);

            LocalDateTime snapTime = date.atTime(hour, 0);
            int dow = snapTime.getDayOfWeek().getValue() - 1; // 0=Mon

            snapshots.add(InventorySnapshot.builder()
                    .store(store)
                    .product(product)
                    .snapshotTime(snapTime)
                    .inventoryLevel(hourlyInventory)
                    .unitsSold(hourlySold)
                    .price(price)
                    .discountPct(discount)
                    .weatherCondition(weather)
                    .isPromotion(isPromo)
                    .competitorPrice(compPrice)
                    .seasonality(seasonality)
                    .hourOfDay(hour)
                    .dayOfWeek(dow)
                    .isWeekend(dow >= 5)
                    .build());
        }

        return snapshots;
    }

    private double sumPeakWeights(int[] hours) {
        double sum = 0;
        for (int h : hours) sum += HOUR_WEIGHTS[h];
        return sum;
    }

    private int parseInt(String s) {
        try { return (int) Double.parseDouble(s.trim()); }
        catch (Exception e) { return 0; }
    }

    private BigDecimal parseBigDecimal(String s) {
        try { return new BigDecimal(s.trim()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }
}
