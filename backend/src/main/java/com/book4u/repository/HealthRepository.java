package com.book4u.repository;

/**
 * Repository interface for database connectivity and diagnostic checks.
 * Adheres to Spring JDBC repository pattern with parameterized queries.
 */
public interface HealthRepository {

    /**
     * Executes a ping query against MySQL to test live connection.
     *
     * @return Raw database product name and version string
     */
    String checkDatabaseConnectivity();
}
