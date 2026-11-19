package com.book4u;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

/**
 * Main application entry point for the Book4u campus book exchange platform.
 * Technology Stack: Spring Boot 3.x, Java 21, Spring JDBC, MySQL 8.x.
 * Note: No Hibernate / JPA is utilized in this architecture.
 */
@SpringBootApplication
public class Book4uApplication {

    private static final Logger log = LoggerFactory.getLogger(Book4uApplication.class);

    public static void main(String[] args) {
        var context = SpringApplication.run(Book4uApplication.class, args);
        Environment env = context.getEnvironment();
        String port = env.getProperty("server.port", "8080");
        log.info("=================================================================");
        log.info("Book4u Platform initialized successfully on port: {}", port);
        log.info("Health Check Endpoint: http://localhost:{}/api/health", port);
        log.info("Database Diagnostics: http://localhost:{}/api/health/db", port);
        log.info("Architecture: Layered Spring JDBC (No ORM / Hibernate)");
        log.info("Tagline: Your Books. Your Campus. Your Community.");
        log.info("=================================================================");
    }
}
