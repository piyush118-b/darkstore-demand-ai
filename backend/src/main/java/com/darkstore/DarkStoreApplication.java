package com.darkstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Dark Store Demand Forecasting System
 *
 * <p>Quick-commerce inventory management platform that predicts short-term demand
 * using an XGBoost ML microservice and automatically triggers reorders to prevent
 * stockouts within 10-20 minute delivery windows.
 */
@SpringBootApplication
@EnableScheduling
public class DarkStoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(DarkStoreApplication.class, args);
    }
}
