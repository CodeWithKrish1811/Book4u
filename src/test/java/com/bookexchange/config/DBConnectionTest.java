package com.bookexchange.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DBConnectionTest {

    @Test
    @DisplayName("Sanitized URL redacts embedded credentials")
    void testGetSanitizedUrl() {
        String sanitized = DBConnection.getSanitizedUrl();
        assertNotNull(sanitized);
        assertFalse(sanitized.contains(":1234@"), "Sanitized URL must not contain plain passwords");
    }

    @Test
    @DisplayName("Configuration validation identifies invalid state")
    void testConfigurationValidation() {
        // DBConnection should have loaded local or test properties
        // Test connection shouldn't throw an uncaught exception
        boolean connected = DBConnection.testConnection();
        // Regardless of live DB availability in test runner, isConfigured() should be boolean
        assertTrue(DBConnection.isConfigured() || DBConnection.getConfigurationError() != null);
    }
}
