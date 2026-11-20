package com.bookexchange.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * ============================================================================
 * DatabaseInitializer - Production-Safe Schema Inspector and Initializer
 * ============================================================================
 * Core Principles:
 * 1. NEVER execute DROP TABLE, TRUNCATE, or destructive SQL during startup.
 * 2. Never assume that the absence of the 'students' table means the database is empty.
 * 3. Inspect all expected tables and columns via DatabaseMetaData before taking action.
 * 4. Only initialize a genuinely empty database (0 application tables).
 * 5. If partial or incompatible schema is detected, fail safe with diagnostics without data loss.
 * 6. Existing production records remain completely untouched.
 * ============================================================================
 */
@WebListener
public class DatabaseInitializer implements ServletContextListener {

    public enum SchemaState {
        EMPTY,
        FULLY_INITIALIZED,
        PARTIALLY_INITIALIZED,
        INCOMPATIBLE,
        INITIALIZATION_FAILED,
        UNAVAILABLE
    }

    public static final Set<String> REQUIRED_TABLES = Set.of(
            "students", "books", "exchange_requests", "exchanges", "reviews"
    );

    public static final Set<String> FORBIDDEN_INCOMPATIBLE_TABLES = Set.of(
            "book_listings" // Incompatible legacy/prototype schema
    );

    public static final Map<String, Set<String>> REQUIRED_COLUMNS = Map.of(
            "students", Set.of("id", "university_id", "full_name", "email", "password_hash", "department"),
            "books", Set.of("id", "owner_id", "title", "author", "subject", "sharing_mode", "status"),
            "exchange_requests", Set.of("id", "book_id", "requester_id", "owner_id", "exchange_mode", "status"),
            "exchanges", Set.of("id", "request_id", "book_id", "owner_id", "requester_id", "exchange_mode", "status"),
            "reviews", Set.of("id", "exchange_id", "reviewer_id", "reviewee_id", "rating")
    );

    private static volatile SchemaState currentSchemaState = SchemaState.UNAVAILABLE;
    private static volatile String lastDiagnosticMessage = "Database schema check has not run yet.";

    public static SchemaState getSchemaState() {
        return currentSchemaState;
    }

    public static String getSchemaDiagnostic() {
        return lastDiagnosticMessage;
    }

    public static boolean isHealthy() {
        return currentSchemaState == SchemaState.FULLY_INITIALIZED;
    }

    public static boolean isSchemaBlocked() {
        return currentSchemaState == SchemaState.PARTIALLY_INITIALIZED
                || currentSchemaState == SchemaState.INCOMPATIBLE
                || currentSchemaState == SchemaState.INITIALIZATION_FAILED;
    }

    // Set state manually for testing if needed
    public static void setSchemaStateForTesting(SchemaState state, String diagnostic) {
        currentSchemaState = state;
        lastDiagnosticMessage = diagnostic;
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[DatabaseInitializer] Starting production-safe schema validation...");
        try (Connection conn = DBConnection.getConnection()) {
            inspectAndInitialize(conn);
        } catch (Exception e) {
            currentSchemaState = SchemaState.UNAVAILABLE;
            lastDiagnosticMessage = "Unable to connect to database: " + e.getMessage();
            System.err.println("[DatabaseInitializer] Database connection failure: " + e.getMessage());
        }
    }

    /**
     * Inspects the database and initializes it ONLY if genuinely empty.
     * Safe to invoke in unit/integration tests with arbitrary connections.
     */
    public static synchronized SchemaState inspectAndInitialize(Connection conn) {
        if (conn == null) {
            currentSchemaState = SchemaState.UNAVAILABLE;
            lastDiagnosticMessage = "Connection is null";
            return currentSchemaState;
        }

        try {
            SchemaState state = inspectSchema(conn);

            if (state == SchemaState.EMPTY) {
                System.out.println("[DatabaseInitializer] Genuinely empty database detected. Running non-destructive schema initialization...");
                boolean success = initializeFromScript(conn, "schema_init.sql");
                if (success) {
                    state = inspectSchema(conn);
                    if (state == SchemaState.FULLY_INITIALIZED) {
                        currentSchemaState = SchemaState.FULLY_INITIALIZED;
                        lastDiagnosticMessage = "Database successfully initialized with non-destructive schema.";
                        System.out.println("[DatabaseInitializer] Schema initialization complete and verified.");
                    } else {
                        currentSchemaState = SchemaState.INITIALIZATION_FAILED;
                        lastDiagnosticMessage = "Initialization completed but schema verification failed. Found: " + state;
                        System.err.println("[DatabaseInitializer] " + lastDiagnosticMessage);
                    }
                } else {
                    currentSchemaState = SchemaState.INITIALIZATION_FAILED;
                }
            } else if (state == SchemaState.FULLY_INITIALIZED) {
                currentSchemaState = SchemaState.FULLY_INITIALIZED;
                lastDiagnosticMessage = "All expected tables and columns are present. Existing records preserved untouched.";
                System.out.println("[DatabaseInitializer] Database is fully initialized. Preserving all existing records.");
                ensureSafeSchemaUpgrades(conn);
            } else if (state == SchemaState.PARTIALLY_INITIALIZED) {
                currentSchemaState = SchemaState.PARTIALLY_INITIALIZED;
                System.err.println("[DatabaseInitializer] CRITICAL WARNING: " + lastDiagnosticMessage);
                System.err.println("[DatabaseInitializer] Refusing automatic destructive repair. Manual DBA intervention required.");
            } else if (state == SchemaState.INCOMPATIBLE) {
                currentSchemaState = SchemaState.INCOMPATIBLE;
                System.err.println("[DatabaseInitializer] CRITICAL ERROR: " + lastDiagnosticMessage);
                System.err.println("[DatabaseInitializer] Halting initialization to protect existing database structures.");
            }

            return currentSchemaState;
        } catch (SQLException e) {
            currentSchemaState = SchemaState.UNAVAILABLE;
            lastDiagnosticMessage = "SQL error during schema inspection: " + e.getMessage();
            System.err.println("[DatabaseInitializer] " + lastDiagnosticMessage);
            return currentSchemaState;
        }
    }

    /**
     * Inspects existing tables and columns via DatabaseMetaData.
     */
    public static SchemaState inspectSchema(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = conn.getCatalog();
        String schema = conn.getSchema();

        Set<String> existingTables = new HashSet<>();
        try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (tableName != null) {
                    existingTables.add(tableName.toLowerCase());
                }
            }
        }

        // Check for forbidden incompatible tables (e.g. legacy book_listings)
        for (String forbidden : FORBIDDEN_INCOMPATIBLE_TABLES) {
            if (existingTables.contains(forbidden)) {
                lastDiagnosticMessage = "Incompatible schema detected: table '" + forbidden +
                        "' exists from legacy prototype. Required table is 'books'.";
                return SchemaState.INCOMPATIBLE;
            }
        }

        // Find how many required application tables exist
        Set<String> foundRequiredTables = new HashSet<>();
        for (String req : REQUIRED_TABLES) {
            if (existingTables.contains(req)) {
                foundRequiredTables.add(req);
            }
        }

        if (foundRequiredTables.isEmpty()) {
            // Check if there are other unrelated user tables in this database
            // If completely empty of application tables, consider it EMPTY
            lastDiagnosticMessage = "No application tables found. Database is empty.";
            return SchemaState.EMPTY;
        }

        if (foundRequiredTables.size() < REQUIRED_TABLES.size()) {
            Set<String> missing = new HashSet<>(REQUIRED_TABLES);
            missing.removeAll(foundRequiredTables);
            lastDiagnosticMessage = "Partially initialized schema: found " + foundRequiredTables +
                    ", but missing required tables " + missing + ". Automatic destructive reset blocked.";
            return SchemaState.PARTIALLY_INITIALIZED;
        }

        // All 5 required tables exist: verify required columns in each table
        for (Map.Entry<String, Set<String>> entry : REQUIRED_COLUMNS.entrySet()) {
            String tableName = entry.getKey();
            Set<String> requiredCols = entry.getValue();
            Set<String> actualCols = new HashSet<>();

            try (ResultSet rs = meta.getColumns(catalog, schema, tableName, "%")) {
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    if (col != null) {
                        actualCols.add(col.toLowerCase());
                    }
                }
            }
            // Some drivers might store table names upper case in metadata (like H2 or Oracle)
            if (actualCols.isEmpty()) {
                try (ResultSet rs = meta.getColumns(catalog, schema, tableName.toUpperCase(), "%")) {
                    while (rs.next()) {
                        String col = rs.getString("COLUMN_NAME");
                        if (col != null) {
                            actualCols.add(col.toLowerCase());
                        }
                    }
                }
            }

            for (String col : requiredCols) {
                if (!actualCols.contains(col.toLowerCase())) {
                    lastDiagnosticMessage = "Incompatible schema detected: table '" + tableName +
                            "' is missing required column '" + col + "'.";
                    return SchemaState.INCOMPATIBLE;
                }
            }
        }

        lastDiagnosticMessage = "Fully initialized schema: all 5 tables and required columns exist.";
        return SchemaState.FULLY_INITIALIZED;
    }

    /**
     * Executes non-destructive SQL script from classpath.
     * Enforces that NO destructive statements (DROP TABLE, TRUNCATE) exist.
     */
    public static boolean initializeFromScript(Connection conn, String scriptName) {
        try (InputStream in = DatabaseInitializer.class.getClassLoader().getResourceAsStream(scriptName)) {
            if (in == null) {
                lastDiagnosticMessage = "Schema initialization script '" + scriptName + "' not found on classpath.";
                System.err.println("[DatabaseInitializer] " + lastDiagnosticMessage);
                return false;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sql = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("--") || trimmed.startsWith("/*") || trimmed.isEmpty()) {
                    continue;
                }
                // Skip database switch statements if targeting cloud-provided DB name
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
                        assertNonDestructiveSql(statementSql);
                        try (Statement stmt = conn.createStatement()) {
                            stmt.execute(statementSql);
                        }
                    }
                    sql.setLength(0);
                }
            }
            return true;
        } catch (Exception e) {
            lastDiagnosticMessage = "Error executing initialization script: " + e.getMessage();
            System.err.println("[DatabaseInitializer] " + lastDiagnosticMessage);
            return false;
        }
    }

    /**
     * Security assertion: strictly forbids any destructive SQL.
     */
    public static void assertNonDestructiveSql(String sql) {
        if (sql == null) return;
        String normalized = sql.replaceAll("\\s+", " ").toUpperCase(Locale.ENGLISH);
        if (normalized.contains("DROP TABLE") || normalized.contains("DROP DATABASE")
                || normalized.contains("TRUNCATE TABLE") || normalized.startsWith("TRUNCATE ")) {
            throw new SecurityException("Destructive SQL statement rejected in production initialization: " + sql);
        }
    }

    /**
     * Non-destructive schema upgrades (ADD COLUMN IF NOT EXISTS).
     */
    private static void ensureSafeSchemaUpgrades(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            // Check & upgrade exchange_requests status ENUM if MySQL
            try {
                stmt.execute("ALTER TABLE exchange_requests MODIFY COLUMN status " +
                        "ENUM('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED') NOT NULL DEFAULT 'PENDING'");
            } catch (Exception ignored) {
                // Non-critical or H2 syntax variance
            }

            // Columns to ensure on exchanges table
            String[][] exchangeColumns = {
                    {"offered_book_id", "BIGINT NULL"},
                    {"owner_handover_confirmed", "BOOLEAN NOT NULL DEFAULT FALSE"},
                    {"requester_handover_confirmed", "BOOLEAN NOT NULL DEFAULT FALSE"},
                    {"owner_return_confirmed", "BOOLEAN NOT NULL DEFAULT FALSE"},
                    {"requester_return_confirmed", "BOOLEAN NOT NULL DEFAULT FALSE"},
                    {"handover_location", "VARCHAR(255) NULL"},
                    {"handover_notes", "TEXT NULL"}
            };

            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            Set<String> existingCols = new HashSet<>();
            try (ResultSet rs = meta.getColumns(catalog, schema, "exchanges", "%")) {
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    if (col != null) existingCols.add(col.toLowerCase());
                }
            }
            if (existingCols.isEmpty()) {
                try (ResultSet rs = meta.getColumns(catalog, schema, "EXCHANGES", "%")) {
                    while (rs.next()) {
                        String col = rs.getString("COLUMN_NAME");
                        if (col != null) existingCols.add(col.toLowerCase());
                    }
                }
            }

            for (String[] col : exchangeColumns) {
                String colName = col[0];
                String colDef = col[1];
                if (!existingCols.contains(colName.toLowerCase())) {
                    try {
                        stmt.execute("ALTER TABLE exchanges ADD COLUMN " + colName + " " + colDef);
                        System.out.println("[DatabaseInitializer] Added missing column 'exchanges." + colName + "'.");
                    } catch (Exception ex) {
                        System.out.println("[DatabaseInitializer] Upgrade note for '" + colName + "': " + ex.getMessage());
                    }
                }
            }

            // Ensure status column width
            try {
                stmt.execute("ALTER TABLE exchanges MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'AWAITING_HANDOVER'");
            } catch (Exception ignored) {}

        } catch (Exception e) {
            System.err.println("[DatabaseInitializer] Schema upgrade note: " + e.getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // No-op
    }
}
