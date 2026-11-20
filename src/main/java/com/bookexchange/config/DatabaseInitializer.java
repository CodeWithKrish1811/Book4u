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
                System.out.println("[DatabaseInitializer] Database tables already present. Checking for schema upgrades...");
                ensureSchemaUpgrades(conn);
                System.out.println("[DatabaseInitializer] Schema upgrade check complete. Ready for requests.");
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

    /**
     * Non-destructive schema migration for existing databases to support
     * complete physical handover and lending return workflows.
     */
    private void ensureSchemaUpgrades(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            // 1. Upgrade exchange_requests status column to include EXPIRED
            try {
                stmt.execute("ALTER TABLE exchange_requests MODIFY COLUMN status " +
                             "ENUM('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED') NOT NULL DEFAULT 'PENDING'");
            } catch (Exception e) {
                System.out.println("[DatabaseInitializer] Migration note (exchange_requests status): " + e.getMessage());
            }

            // 2. Add dual-confirmation and tracking columns to exchanges table if missing
            String[][] exchangeColumns = {
                {"offered_book_id", "BIGINT NULL"},
                {"owner_handover_confirmed", "BOOLEAN NOT NULL DEFAULT FALSE"},
                {"requester_handover_confirmed", "BOOLEAN NOT NULL DEFAULT FALSE"},
                {"owner_return_confirmed", "BOOLEAN NOT NULL DEFAULT FALSE"},
                {"requester_return_confirmed", "BOOLEAN NOT NULL DEFAULT FALSE"},
                {"handover_location", "VARCHAR(255) NULL"},
                {"handover_notes", "TEXT NULL"}
            };

            for (String[] col : exchangeColumns) {
                String colName = col[0];
                String colDef = col[1];
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = 'exchanges' AND column_name = '" + colName + "'")) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        try (Statement alterStmt = conn.createStatement()) {
                            alterStmt.execute("ALTER TABLE exchanges ADD COLUMN " + colName + " " + colDef);
                            System.out.println("[DatabaseInitializer] Added column 'exchanges." + colName + "' successfully.");
                        }
                    }
                } catch (Exception ex) {
                    System.out.println("[DatabaseInitializer] Migration check note for 'exchanges." + colName + "': " + ex.getMessage());
                }
            }

            // 3. Relax start_date to allow NULL initially (until physical handover occurs)
            try {
                stmt.execute("ALTER TABLE exchanges MODIFY COLUMN start_date DATE NULL");
            } catch (Exception e) {
                System.out.println("[DatabaseInitializer] Migration note (exchanges.start_date): " + e.getMessage());
            }

            // 4. Update exchanges status column to VARCHAR(32) to support all physical lifecycle states
            try {
                stmt.execute("ALTER TABLE exchanges MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'AWAITING_HANDOVER'");
            } catch (Exception e) {
                System.out.println("[DatabaseInitializer] Migration note (exchanges.status): " + e.getMessage());
            }

        } catch (Exception e) {
            System.err.println("[DatabaseInitializer] Non-destructive migration exception: " + e.getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Cleanup if needed
    }
}
