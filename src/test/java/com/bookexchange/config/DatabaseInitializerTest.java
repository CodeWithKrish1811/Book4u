package com.bookexchange.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================================
 * DatabaseInitializerTest - Production-Safe Initialization & Schema Audit Tests
 * ============================================================================
 * Covers all 9 required production safety scenarios in isolated H2 MySQL mode:
 * 1. Empty DB initializes successfully
 * 2. Existing valid DB is not reset
 * 3. Existing records remain unchanged after startup
 * 4. Missing students but existing books detected as partial
 * 5. Missing exchanges detected safely
 * 6. Incompatible columns detected and reported
 * 7. Incompatible legacy tables detected (e.g. book_listings)
 * 8. Initialization is idempotent
 * 9. No destructive SQL executed during startup
 * 10. Initialization failure handled safely
 * ============================================================================
 */
public class DatabaseInitializerTest {

    private String dbName;
    private Connection conn;

    @BeforeEach
    void setUp() throws SQLException {
        dbName = "test_init_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + dbName + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        conn = DriverManager.getConnection(url, "sa", "");
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            try (Statement s = conn.createStatement()) {
                s.execute("DROP ALL OBJECTS");
            }
            conn.close();
        }
    }

    @Test
    @DisplayName("Scenario 1: Fresh empty DB initializes successfully to FULLY_INITIALIZED")
    void testEmptyDatabaseInitializesSuccessfully() throws Exception {
        DatabaseInitializer.SchemaState state = DatabaseInitializer.inspectAndInitialize(conn);

        assertEquals(DatabaseInitializer.SchemaState.FULLY_INITIALIZED, state,
                "Fresh database must initialize to FULLY_INITIALIZED");
        assertTrue(DatabaseInitializer.isHealthy(), "System must be healthy after initializing empty DB");

        // Verify all 5 tables exist
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN ('students', 'books', 'exchange_requests', 'exchanges', 'reviews')")) {
            assertTrue(rs.next());
            assertEquals(5, rs.getInt(1), "All 5 core tables must exist");
        }
    }

    @Test
    @DisplayName("Scenario 2: Existing valid DB is preserved and not reset")
    void testExistingValidDatabaseIsNotReset() throws Exception {
        // Initialize first
        DatabaseInitializer.SchemaState firstState = DatabaseInitializer.inspectAndInitialize(conn);
        assertEquals(DatabaseInitializer.SchemaState.FULLY_INITIALIZED, firstState);

        // Run inspection/initialization a second time
        DatabaseInitializer.SchemaState secondState = DatabaseInitializer.inspectAndInitialize(conn);
        assertEquals(DatabaseInitializer.SchemaState.FULLY_INITIALIZED, secondState,
                "Already initialized DB must remain FULLY_INITIALIZED without reset");
    }

    @Test
    @DisplayName("Scenario 3: Existing student and book records remain completely untouched")
    void testExistingRecordsRemainUnchangedAfterStartup() throws Exception {
        // Initialize
        DatabaseInitializer.inspectAndInitialize(conn);

        // Insert real student record
        try (Statement s = conn.createStatement()) {
            s.execute("INSERT INTO students (university_id, full_name, email, password_hash, department) " +
                    "VALUES ('STU-001', 'Alice Smith', 'alice@campus.edu', 'hash123', 'Computer Science')");
        }

        // Run inspectAndInitialize again (simulating subsequent application restart)
        DatabaseInitializer.inspectAndInitialize(conn);

        // Verify student data was not dropped or truncated
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT full_name, email FROM students WHERE university_id = 'STU-001'")) {
            assertTrue(rs.next(), "Existing student record must survive application startup");
            assertEquals("Alice Smith", rs.getString("full_name"));
            assertEquals("alice@campus.edu", rs.getString("email"));
        }
    }

    @Test
    @DisplayName("Scenario 4: Missing students but existing books table detected as PARTIALLY_INITIALIZED")
    void testMissingStudentsWithExistingBooksDetectedAsPartial() throws Exception {
        // Create only books table
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE books (id BIGINT AUTO_INCREMENT PRIMARY KEY, owner_id BIGINT, title VARCHAR(255), author VARCHAR(255), subject VARCHAR(128), sharing_mode VARCHAR(32), status VARCHAR(32))");
        }

        DatabaseInitializer.SchemaState state = DatabaseInitializer.inspectAndInitialize(conn);

        assertEquals(DatabaseInitializer.SchemaState.PARTIALLY_INITIALIZED, state,
                "Missing students table while books exists must be classified as PARTIALLY_INITIALIZED");
        assertTrue(DatabaseInitializer.isSchemaBlocked(), "Schema must be marked blocked to protect data");
        assertTrue(DatabaseInitializer.getSchemaDiagnostic().contains("missing required tables"));
    }

    @Test
    @DisplayName("Scenario 5: Missing exchanges table detected safely without destructive reset")
    void testMissingExchangesDetectedSafely() throws Exception {
        // Create 4 tables, leaving out exchanges
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE students (id BIGINT PRIMARY KEY, university_id VARCHAR(64), full_name VARCHAR(128), email VARCHAR(255), password_hash VARCHAR(255), department VARCHAR(128))");
            s.execute("CREATE TABLE books (id BIGINT PRIMARY KEY, owner_id BIGINT, title VARCHAR(255), author VARCHAR(255), subject VARCHAR(128), sharing_mode VARCHAR(32), status VARCHAR(32))");
            s.execute("CREATE TABLE exchange_requests (id BIGINT PRIMARY KEY, book_id BIGINT, requester_id BIGINT, owner_id BIGINT, exchange_mode VARCHAR(32), status VARCHAR(32))");
            s.execute("CREATE TABLE reviews (id BIGINT PRIMARY KEY, exchange_id BIGINT, reviewer_id BIGINT, reviewee_id BIGINT, rating INT)");
        }

        DatabaseInitializer.SchemaState state = DatabaseInitializer.inspectAndInitialize(conn);

        assertEquals(DatabaseInitializer.SchemaState.PARTIALLY_INITIALIZED, state,
                "Schema missing exchanges must be flagged as PARTIALLY_INITIALIZED");
        assertTrue(DatabaseInitializer.isSchemaBlocked());
        assertTrue(DatabaseInitializer.getSchemaDiagnostic().contains("exchanges"));
    }

    @Test
    @DisplayName("Scenario 6: Missing required column in books table detected as INCOMPATIBLE")
    void testMissingRequiredColumnDetectedAsIncompatible() throws Exception {
        // Create all 5 tables, but omit 'subject' column in books
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE students (id BIGINT PRIMARY KEY, university_id VARCHAR(64), full_name VARCHAR(128), email VARCHAR(255), password_hash VARCHAR(255), department VARCHAR(128))");
            s.execute("CREATE TABLE books (id BIGINT PRIMARY KEY, owner_id BIGINT, title VARCHAR(255), author VARCHAR(255), sharing_mode VARCHAR(32), status VARCHAR(32))"); // Missing subject!
            s.execute("CREATE TABLE exchange_requests (id BIGINT PRIMARY KEY, book_id BIGINT, requester_id BIGINT, owner_id BIGINT, exchange_mode VARCHAR(32), status VARCHAR(32))");
            s.execute("CREATE TABLE exchanges (id BIGINT PRIMARY KEY, request_id BIGINT, book_id BIGINT, owner_id BIGINT, requester_id BIGINT, exchange_mode VARCHAR(32), status VARCHAR(32))");
            s.execute("CREATE TABLE reviews (id BIGINT PRIMARY KEY, exchange_id BIGINT, reviewer_id BIGINT, reviewee_id BIGINT, rating INT)");
        }

        DatabaseInitializer.SchemaState state = DatabaseInitializer.inspectAndInitialize(conn);

        assertEquals(DatabaseInitializer.SchemaState.INCOMPATIBLE, state,
                "Schema missing required column must be classified as INCOMPATIBLE");
        assertTrue(DatabaseInitializer.isSchemaBlocked());
        assertTrue(DatabaseInitializer.getSchemaDiagnostic().contains("subject"));
    }

    @Test
    @DisplayName("Scenario 7: Forbidden legacy table book_listings detected as INCOMPATIBLE")
    void testLegacyBookListingsDetectedAsIncompatible() throws Exception {
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE book_listings (listing_id BIGINT PRIMARY KEY, title VARCHAR(255))");
        }

        DatabaseInitializer.SchemaState state = DatabaseInitializer.inspectAndInitialize(conn);

        assertEquals(DatabaseInitializer.SchemaState.INCOMPATIBLE, state,
                "Presence of legacy book_listings table must be classified as INCOMPATIBLE");
        assertTrue(DatabaseInitializer.isSchemaBlocked());
        assertTrue(DatabaseInitializer.getSchemaDiagnostic().contains("book_listings"));
    }

    @Test
    @DisplayName("Scenario 8: Schema initialization is completely idempotent")
    void testInitializationIsIdempotent() {
        for (int i = 0; i < 3; i++) {
            DatabaseInitializer.SchemaState state = DatabaseInitializer.inspectAndInitialize(conn);
            assertEquals(DatabaseInitializer.SchemaState.FULLY_INITIALIZED, state,
                    "Initialization run #" + (i + 1) + " must remain FULLY_INITIALIZED");
        }
    }

    @Test
    @DisplayName("Scenario 9: Security assertions reject any destructive SQL (DROP / TRUNCATE)")
    void testAssertNonDestructiveSqlBlocksDestructiveCommands() {
        assertThrows(SecurityException.class, () ->
                DatabaseInitializer.assertNonDestructiveSql("DROP TABLE students;"));

        assertThrows(SecurityException.class, () ->
                DatabaseInitializer.assertNonDestructiveSql("DROP TABLE IF EXISTS books;"));

        assertThrows(SecurityException.class, () ->
                DatabaseInitializer.assertNonDestructiveSql("TRUNCATE TABLE exchanges;"));

        assertThrows(SecurityException.class, () ->
                DatabaseInitializer.assertNonDestructiveSql("TRUNCATE reviews;"));

        assertThrows(SecurityException.class, () ->
                DatabaseInitializer.assertNonDestructiveSql("DROP DATABASE book4u;"));

        // Non-destructive SQL must pass without exception
        assertDoesNotThrow(() ->
                DatabaseInitializer.assertNonDestructiveSql("CREATE TABLE IF NOT EXISTS students (id BIGINT);"));

        assertDoesNotThrow(() ->
                DatabaseInitializer.assertNonDestructiveSql("ALTER TABLE exchanges ADD COLUMN notes TEXT;"));
    }

    @Test
    @DisplayName("Scenario 10: SQL initialization failure reports INITIALIZATION_FAILED")
    void testInitializationFailureReportedCorrectly() {
        boolean result = DatabaseInitializer.initializeFromScript(conn, "non_existent_file.sql");
        assertFalse(result, "Missing script must fail gracefully");
        assertTrue(DatabaseInitializer.getSchemaDiagnostic().contains("not found on classpath"));
    }
}
