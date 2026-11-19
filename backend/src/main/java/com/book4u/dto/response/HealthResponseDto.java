package com.book4u.dto.response;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for basic application health check response.
 * Follows contract: { "status": "UP", "application": "Book4u" }
 */
public class HealthResponseDto {

    private String status;
    private String application;

    public HealthResponseDto() {
    }

    public HealthResponseDto(String status, String application) {
        this.status = status;
        this.application = application;
    }

    public static HealthResponseDto up() {
        return new HealthResponseDto("UP", "Book4u");
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }
}
