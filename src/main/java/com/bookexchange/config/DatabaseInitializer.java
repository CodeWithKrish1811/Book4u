package com.bookexchange.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Automatically initializes database tables and seed data upon web application startup
 * if connected to a fresh database (e.g. Railway, Docker, or fresh MySQL instance).
 */
@WebListener
public class DatabaseInitializer implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[DatabaseInitializer] Checking database schema status...");

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // Check if 'students' table exists in current active database
            boolean tablesExist = false;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'students'")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    tablesExist = true;
                }
            }

            if (tablesExist) {
                System.out.println("[DatabaseInitializer] Database tables already present. Ready for requests.");
                return;
            }

            System.out.println("[DatabaseInitializer] Fresh database detected. Executing book_exchange_db.sql...");
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("book_exchange_db.sql")) {
                if (in == null) {
                    System.err.println("[DatabaseInitializer] Warning: book_exchange_db.sql not found on classpath.");
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sql = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("--") || trimmed.startsWith("/*") || trimmed.isEmpty()) {
                        continue;
                    }
                    // Skip database creation/switch statements so it targets the cloud database (e.g. 'railway')
                    if (trimmed.toUpperCase().startsWith("CREATE DATABASE") || trimmed.toUpperCase().startsWith("USE ")) {
                        continue;
                    }
                    sql.append(line).append("\n");
                    if (trimmed.endsWith(";")) {
                        String statementSql = sql.toString().trim();
                        if (statementSql.endsWith(";")) {
                            statementSql = statementSql.substring(0, statementSql.length() - 1);
                        }
                        if (!statementSql.isBlank()) {
                            try (Statement execStmt = conn.createStatement()) {
                                execStmt.execute(statementSql);
                            } catch (Exception ex) {
                                System.err.println("[DatabaseInitializer] Note during execution: " + ex.getMessage());
                            }
                        }
                        sql.setLength(0);
                    }
                }
                System.out.println("[DatabaseInitializer] Database schema and seed data initialized successfully!");
            }

        } catch (Exception e) {
            System.err.println("[DatabaseInitializer] Database auto-initialization note: " + e.getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Cleanup if needed
    }
}
