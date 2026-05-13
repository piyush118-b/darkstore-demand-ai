package com.darkstore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Application-wide configuration beans.
 */
@Configuration
public class AppConfig implements WebMvcConfigurer {

    @Value("${ml-service.timeout-seconds:10}")
    private int mlServiceTimeoutSeconds;

    /**
     * RestTemplate configured with a timeout for ML microservice calls.
     * Prevents the reorder engine from blocking indefinitely if the ML service is slow.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(mlServiceTimeoutSeconds))
                .setReadTimeout(Duration.ofSeconds(mlServiceTimeoutSeconds))
                .build();
    }

    /**
     * CORS configuration — allows the React dashboard (port 3000) to
     * communicate with this backend (port 8080) in local development.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000", "http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
