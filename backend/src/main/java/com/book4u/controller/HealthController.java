package com.book4u.controller;

import com.book4u.dto.response.DatabaseHealthDto;
import com.book4u.dto.response.HealthResponseDto;
import com.book4u.service.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for platform health and connectivity verification.
 * Controllers do not execute SQL queries and delegate directly to Service components.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * Standard health check endpoint for container orchestrators, load balancers, and frontend.
     * Response: { "status": "UP", "application": "Book4u" }
     */
    @GetMapping
    public ResponseEntity<HealthResponseDto> getHealth() {
        log.debug("GET /api/health requested");
        HealthResponseDto response = healthService.getApplicationHealth();
        return ResponseEntity.ok(response);
    }

    /**
     * Diagnostic endpoint to verify live Spring JDBC and MySQL connectivity.
     */
    @GetMapping("/db")
    public ResponseEntity<DatabaseHealthDto> getDatabaseHealth() {
        log.debug("GET /api/health/db requested");
        DatabaseHealthDto response = healthService.getDatabaseHealth();
        return ResponseEntity.ok(response);
    }
}
