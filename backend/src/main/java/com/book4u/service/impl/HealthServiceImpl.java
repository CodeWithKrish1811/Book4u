package com.book4u.service.impl;

import com.book4u.dto.response.DatabaseHealthDto;
import com.book4u.dto.response.HealthResponseDto;
import com.book4u.repository.HealthRepository;
import com.book4u.service.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Service implementation for health and diagnostic operations.
 * Coordinates between repositories and presents clean DTO responses.
 */
@Service
public class HealthServiceImpl implements HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthServiceImpl.class);

    private final HealthRepository healthRepository;

    public HealthServiceImpl(HealthRepository healthRepository) {
        this.healthRepository = healthRepository;
    }

    @Override
    public HealthResponseDto getApplicationHealth() {
        return HealthResponseDto.up();
    }

    @Override
    public DatabaseHealthDto getDatabaseHealth() {
        long startTime = System.currentTimeMillis();
        try {
            String dbVersion = healthRepository.checkDatabaseConnectivity();
            long duration = System.currentTimeMillis() - startTime;
            log.info("Database connection verified successfully. Engine: MySQL, Version: {}, Latency: {}ms", dbVersion, duration);
            return new DatabaseHealthDto(
                    "CONNECTED",
                    "MySQL Relational Database",
                    dbVersion,
                    duration,
                    "Database connection established successfully via Spring JDBC."
            );
        } catch (DataAccessException ex) {
            long duration = System.currentTimeMillis() - startTime;
            String rootCauseMessage = ex.getMostSpecificCause() != null ?
                    ex.getMostSpecificCause().getMessage() : ex.getMessage();
            rootCauseMessage = sanitizeMessage(rootCauseMessage);
            log.warn("Database connection check failed after {}ms: {}", duration, rootCauseMessage);
            return new DatabaseHealthDto(
                    "DISCONNECTED",
                    "MySQL Relational Database",
                    "Unknown",
                    duration,
                    "Database unreachable: " + rootCauseMessage + ". Please verify MySQL service status and credentials in .env"
            );
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            String sanitized = sanitizeMessage(ex.getMessage());
            log.warn("Unexpected exception during database connection check: {}", sanitized);
            return new DatabaseHealthDto(
                    "ERROR",
                    "MySQL Relational Database",
                    "Unknown",
                    duration,
                    "Unexpected error during connection test: " + sanitized
            );
        }
    }

    private String sanitizeMessage(String msg) {
        if (msg == null) return "Unknown database error";
        return msg.replaceAll("(?i)(password=)[^&;\\s]*", "$1****");
    }
}
