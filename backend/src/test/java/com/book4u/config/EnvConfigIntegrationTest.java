package com.book4u.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "spring.datasource.hikari.initialization-fail-timeout=-1"
})
class EnvConfigIntegrationTest {

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("Verify Spring Boot environment successfully resolves application and database properties")
    void shouldResolveConfigProperties() {
        assertNotNull(environment, "Environment context must not be null");
        assertEquals("Book4u", environment.getProperty("spring.application.name"));

        // Verify datasource properties are mapped to DB_URL, DB_USERNAME, DB_PASSWORD placeholders
        String dbUrl = environment.getProperty("spring.datasource.url");
        assertNotNull(dbUrl, "Database URL must be resolved");

        String dbUser = environment.getProperty("spring.datasource.username");
        assertEquals("root", dbUser, "Default username should resolve to root");

        String corsOrigins = environment.getProperty("cors.allowed-origins");
        assertNotNull(corsOrigins, "CORS allowed origins must be resolved");
    }
}
