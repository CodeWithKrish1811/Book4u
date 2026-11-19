package com.book4u.repository.impl;

import com.book4u.repository.HealthRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Spring JDBC implementation of HealthRepository.
 * Uses JdbcTemplate to verify relational database connectivity.
 */
@Repository
public class HealthRepositoryImpl implements HealthRepository {

    private static final Logger log = LoggerFactory.getLogger(HealthRepositoryImpl.class);

    private final JdbcTemplate jdbcTemplate;

    public HealthRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String checkDatabaseConnectivity() {
        log.debug("Executing ping query against database via Spring JdbcTemplate");
        // Using pure parameterized JDBC query
        return jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
    }
}
