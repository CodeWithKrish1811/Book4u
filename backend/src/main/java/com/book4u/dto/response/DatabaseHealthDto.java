package com.book4u.dto.response;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for detailed MySQL JDBC connectivity diagnostics.
 */
public class DatabaseHealthDto {

    private String status;
    private String databaseEngine;
    private String databaseVersion;
    private long responseTimeMs;
    private String message;
    private LocalDateTime timestamp;

    public DatabaseHealthDto() {
        this.timestamp = LocalDateTime.now();
    }

    public DatabaseHealthDto(String status, String databaseEngine, String databaseVersion, long responseTimeMs, String message) {
        this.status = status;
        this.databaseEngine = databaseEngine;
        this.databaseVersion = databaseVersion;
        this.responseTimeMs = responseTimeMs;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDatabaseEngine() {
        return databaseEngine;
    }

    public void setDatabaseEngine(String databaseEngine) {
        this.databaseEngine = databaseEngine;
    }

    public String getDatabaseVersion() {
        return databaseVersion;
    }

    public void setDatabaseVersion(String databaseVersion) {
        this.databaseVersion = databaseVersion;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
