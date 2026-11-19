package com.book4u.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * Spring JDBC & DataSource Configuration.
 * Configures pure JDBC components (JdbcTemplate and NamedParameterJdbcTemplate).
 * Strictly avoids Hibernate / JPA.
 * Credentials and passwords are strictly protected and never exposed.
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    public DatabaseConfig(
            @Value("${spring.datasource.url:}") String dbUrl,
            @Value("${spring.datasource.username:}") String dbUser,
            @Value("${spring.datasource.driver-class-name:}") String driverClass) {
        log.info("=================================================================");
        log.info("Initializing Spring JDBC Database Configuration for Book4u");
        log.info("DataSource URL: {}", maskUrl(dbUrl));
        log.info("DataSource Username: {}", dbUser);
        log.info("DataSource Driver: {}", driverClass);
        log.info("Password Status: [PROTECTED / NEVER LOGGED]");
        log.info("Data Access: Pure Spring JDBC (PreparedStatement / Parameterized SQL)");
        log.info("=================================================================");
    }

    /**
     * Safely sanitizes database connection URLs in log streams.
     */
    private static String maskUrl(String url) {
        if (url == null || url.isBlank()) {
            return "[NOT SET]";
        }
        return url.replaceAll("(?i)(password=)[^&;]*", "$1****");
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        log.info("Registering JdbcTemplate bean for parameterized SQL execution");
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        log.info("Registering NamedParameterJdbcTemplate bean for parameterized SQL execution");
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
