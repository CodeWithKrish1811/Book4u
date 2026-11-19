package com.book4u.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * Spring JDBC & DataSource Configuration.
 * Configures pure JDBC components (JdbcTemplate and NamedParameterJdbcTemplate).
 * Strictly avoids Hibernate / JPA.
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    public DatabaseConfig() {
        log.info("Initializing Spring JDBC Database Configuration for Book4u");
    }

    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        log.info("Registering NamedParameterJdbcTemplate bean for parameterized SQL execution");
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
