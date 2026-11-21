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
 * Core Production-Safety Rules:
 * 1. EMPTY DETECTION: A database is EMPTY only when it contains zero user/application tables.
 *    If ANY unrelated tables exist, classify as INCOMPATIBLE and NEVER initialize into it.
 * 2. STARTUP SAFETY: If schema is partial, incompatible, failed, or unavailable, startup fails fast.
 * 3. NO AUTOMATIC ALTERATIONS: Existing schemas are strictly inspected, never modified/altered on startup.
 * 4. SEED DATA ISOLATION: Production initialization creates DDL only, zero fake student or book records.
 * 5. ATOMIC / STRICT EXECUTION: Any failed SQL statement during initialization halts and reports failure.
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
                || currentSchemaState == SchemaState.INITIALIZATION_FAILED
                || currentSchemaState == SchemaState.UNAVAILABLE;
    }

    // Allows resetting or configuring state during unit tests
    public static void setSchemaStateForTesting(SchemaState state, String diagnostic) {
        currentSchemaState = state;
        lastDiagnosticMessage = diagnostic;
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[DatabaseInitializer] Starting production-safe schema validation...");
        try (Connection conn = DBConnection.getConnection()) {
            performStartupValidation(conn);
            System.out.println("[DatabaseInitializer] Database schema verified successfully. Application startup proceeding.");
        } catch (SQLException e) {
            currentSchemaState = SchemaState.UNAVAILABLE;
            lastDiagnosticMessage = "Unable to connect to database during application startup: " + e.getMessage();
            String fatalError = "CRITICAL: Database connection failed during startup: " + e.getMessage();
            System.err.println("[DatabaseInitializer] " + fatalError);
            throw new IllegalStateException(fatalError, e);
        }
    }

    /**
     * Executes schema validation and halts application startup if schema is unusable.
     * Throws IllegalStateException when schema is partial, incompatible, failed, or unavailable.
     */
    public static synchronized void performStartupValidation(Connection conn) {
        if (conn == null) {
            currentSchemaState = SchemaState.UNAVAILABLE;
            lastDiagnosticMessage = "Database connection is null.";
            throw new IllegalStateException("CRITICAL: Database connection is null during startup.");
        }

        SchemaState state = inspectAndInitialize(conn);
        if (state != SchemaState.FULLY_INITIALIZED) {
            String fatalError = "CRITICAL: Application startup blocked because database schema is [" +
                    state + "]. Diagnostic: " + lastDiagnosticMessage;
            System.err.println("[DatabaseInitializer] " + fatalError);
            throw new IllegalStateException(fatalError);
        }
    }

    /**
     * Inspects the database and initializes it ONLY if genuinely empty.
     * Never performs ALTER TABLE or updates on existing schemas.
     */
    public static synchronized SchemaState inspectAndInitialize(Connection conn) {
        if (conn == null) {
            currentSchemaState = SchemaState.UNAVAILABLE;
            lastDiagnosticMessage = "Database connection is null.";
            return currentSchemaState;
        }

        try {
            SchemaState state = inspectSchema(conn);

            if (state == SchemaState.EMPTY) {
                System.out.println("[DatabaseInitializer] Genuinely empty database detected (0 tables). Executing non-destructive schema_init.sql...");
                boolean success = initializeFromScript(conn, "schema_init.sql");
                if (success) {
                    state = inspectSchema(conn);
                    if (state == SchemaState.FULLY_INITIALIZED) {
                        currentSchemaState = SchemaState.FULLY_INITIALIZED;
                        lastDiagnosticMessage = "Database successfully initialized with non-destructive schema.";
                        System.out.println("[DatabaseInitializer] Schema initialization complete and verified.");
                    } else {
                        currentSchemaState = SchemaState.INITIALIZATION_FAILED;
                        lastDiagnosticMessage = "Initialization completed but verification failed. Current state: " + state;
                        System.err.println("[DatabaseInitializer] " + lastDiagnosticMessage);
                    }
                } else {
                    currentSchemaState = SchemaState.INITIALIZATION_FAILED;
                }
            } else if (state == SchemaState.FULLY_INITIALIZED) {
                currentSchemaState = SchemaState.FULLY_INITIALIZED;
                lastDiagnosticMessage = "All expected tables and columns are present. Existing records preserved untouched.";
                System.out.println("[DatabaseInitializer] Database is fully initialized. Preserving all existing records. No schema alterations executed.");
            } else if (state == SchemaState.PARTIALLY_INITIALIZED) {
                currentSchemaState = SchemaState.PARTIALLY_INITIALIZED;
                System.err.println("[DatabaseInitializer] CRITICAL WARNING: " + lastDiagnosticMessage);
                System.err.println("[DatabaseInitializer] Refusing automatic destructive repair. Startup blocked.");
            } else if (state == SchemaState.INCOMPATIBLE) {
                currentSchemaState = SchemaState.INCOMPATIBLE;
                System.err.println("[DatabaseInitializer] CRITICAL ERROR: " + lastDiagnosticMessage);
                System.err.println("[DatabaseInitializer] Refusing to initialize into unknown or incompatible database. Startup blocked.");
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
     * Enforces:
     * - EMPTY: Exactly zero user tables exist.
     * - INCOMPATIBLE: Any table exists that does not belong to REQUIRED_TABLES.
     * - PARTIALLY_INITIALIZED: Only tables from REQUIRED_TABLES exist, but fewer than all 5.
     * - FULLY_INITIALIZED: All 5 tables and required columns exist.
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

        // Rule 1: A database is EMPTY only when it contains zero user tables
        if (existingTables.isEmpty()) {
            lastDiagnosticMessage = "Genuinely empty database detected (0 tables).";
            return SchemaState.EMPTY;
        }

        // Rule 2: If any table exists that is not a known application table, classify as INCOMPATIBLE
        Set<String> unrelatedTables = new HashSet<>();
        for (String tbl : existingTables) {
            if (!REQUIRED_TABLES.contains(tbl)) {
                unrelatedTables.add(tbl);
            }
        }

        if (!unrelatedTables.isEmpty()) {
            lastDiagnosticMessage = "Unrelated/incompatible tables detected in database: " + unrelatedTables +
                    ". Database is not empty and does not match Book4u application schema. Refusing to initialize into unknown database.";
            return SchemaState.INCOMPATIBLE;
        }

        // Rule 3: All existing tables are known, but check if all 5 required tables are present
        if (existingTables.size() < REQUIRED_TABLES.size()) {
            Set<String> missing = new HashSet<>(REQUIRED_TABLES);
            missing.removeAll(existingTables);
            lastDiagnosticMessage = "Partially initialized schema: found " + existingTables +
                    ", but missing required tables " + missing + ". Automatic destructive reset blocked.";
            return SchemaState.PARTIALLY_INITIALIZED;
        }

        // Rule 4: All 5 required tables exist: verify required columns in each table
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
            // Some JDBC drivers / in-memory databases report uppercase table names
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
     * Enforces:
     * - NO destructive statements (DROP TABLE, TRUNCATE).
     * - Strictly halts and fails if ANY statement throws an exception.
     */
    public static boolean initializeFromScript(Connection conn, String scriptName) {
        try (InputStream in = DatabaseInitializer.class.getClassLoader().getResourceAsStream(scriptName)) {
            if (in == null) {
                lastDiagnosticMessage = "Schema initialization script '" + scriptName + "' not found on classpath.";
                System.err.println("[DatabaseInitializer] " + lastDiagnosticMessage);
                currentSchemaState = SchemaState.INITIALIZATION_FAILED;
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
                        } catch (SQLException ex) {
                            lastDiagnosticMessage = "SQL error executing statement [" + statementSql + "]: " + ex.getMessage();
                            System.err.println("[DatabaseInitializer] " + lastDiagnosticMessage);
                            currentSchemaState = SchemaState.INITIALIZATION_FAILED;
                            return false;
                        }
                    }
                    sql.setLength(0);
                }
            }
            return true;
        } catch (Exception e) {
            lastDiagnosticMessage = "Error executing initialization script: " + e.getMessage();
            System.err.println("[DatabaseInitializer] " + lastDiagnosticMessage);
            currentSchemaState = SchemaState.INITIALIZATION_FAILED;
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

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // No-op
    }
}
