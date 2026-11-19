package com.book4u.service;

import com.book4u.dto.response.DatabaseHealthDto;
import com.book4u.dto.response.HealthResponseDto;

/**
 * Service interface for platform and database health verification.
 */
public interface HealthService {

    /**
     * Checks core application status.
     *
     * @return HealthResponseDto containing status and application name
     */
    HealthResponseDto getApplicationHealth();

    /**
     * Checks relational database connectivity and performance via JDBC repository.
     *
     * @return DatabaseHealthDto containing diagnostic results
     */
    DatabaseHealthDto getDatabaseHealth();
}
