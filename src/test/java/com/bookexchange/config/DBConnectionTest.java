package com.bookexchange.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class DBConnectionTest {

    @AfterEach
    void tearDown() {
        DBConnection.resetTestConnectionOverride();
    }

    @Test
    @DisplayName("Sanitize URL: returns [NOT CONFIGURED] for null or blank inputs")
    void testSanitizeUrlNullOrBlank() {
        assertEquals("[NOT CONFIGURED]", DBConnection.sanitizeUrl(null));
        assertEquals("[NOT CONFIGURED]", DBConnection.sanitizeUrl(""));
        assertEquals("[NOT CONFIGURED]", DBConnection.sanitizeUrl("   "));
    }

    @Test
    @DisplayName("Sanitize URL: redacts authority credentials across multiple URL patterns")
    void testSanitizeUrlAuthorityCredentials() {
        // Pattern 1: Cloud proxy URI with complex password
        String url1 = "mysql://root:secretPass123@junction.proxy.rlwy.net:12345/railway?useSSL=false";
        String s1 = DBConnection.sanitizeUrl(url1);
        assertFalse(s1.contains("secretPass123"), "Password must not appear in sanitized URL");
        assertTrue(s1.contains("***:***@junction.proxy.rlwy.net:12345/railway"), "Expected masked authority format");

        // Pattern 2: JDBC URL with special characters in password
        String url2 = "jdbc:mysql://admin:P@ss:w0rd!@remote.database.com:3306/mydb";
        String s2 = DBConnection.sanitizeUrl(url2);
        assertFalse(s2.contains("P@ss:w0rd!"), "Password with special characters must be masked");
        assertTrue(s2.contains("***:***@remote.database.com:3306/mydb"), "Expected masked authority format");

        // Pattern 3: Standard cloud internal host
        String url3 = "mysql://admin-user:tok_abc123xyz@10.0.0.5:3306/exchange_db";
        String s3 = DBConnection.sanitizeUrl(url3);
        assertFalse(s3.contains("tok_abc123xyz"), "Token credential must be masked");
        assertTrue(s3.contains("***:***@10.0.0.5:3306/exchange_db"));

        // Pattern 4: Authority with username only
        String url4 = "mysql://service_user@db.internal:3306/exchange_db";
        String s4 = DBConnection.sanitizeUrl(url4);
        assertTrue(s4.contains("***@db.internal:3306/exchange_db"));
    }

    @Test
    @DisplayName("Sanitize URL: redacts password query parameters")
    void testSanitizeUrlQueryCredentials() {
        String url = "jdbc:mysql://db.railway.internal:3306/railway?password=supersecret&user=appuser";
        String s = DBConnection.sanitizeUrl(url);
        assertFalse(s.contains("supersecret"), "Query password parameter must be redacted");
        assertTrue(s.contains("password=***"));
        assertTrue(s.contains("user=appuser"));
    }

    @Test
    @DisplayName("Sanitize URL: leaves clean JDBC URL untouched")
    void testSanitizeUrlCleanUrl() {
        String cleanUrl = "jdbc:mysql://mysql.railway.internal:3306/railway?useSSL=false&serverTimezone=UTC";
        assertEquals(cleanUrl, DBConnection.sanitizeUrl(cleanUrl));
    }

    @Test
    @DisplayName("URL Parser: decodes percent-encoded username and password with special characters (@, :, /, ?, #, %)")
    void testParseDatabaseUrlEncodedSpecialCharacters() {
        // RFC 3986 percent-encoded user and password:
        // user: student@univ.edu (%40)
        // pass: p@ss:w/o?r#d%1 (%40, %3A, %2F, %3F, %23, %25)
        String raw = "mysql://student%40univ.edu:p%40ss%3A%2F%3F%23%251@db.railway.internal:3306/railway?useSSL=false";
        DBConnection.ParsedDatabaseUrl parsed = DBConnection.parseDatabaseUrl(raw);

        assertNotNull(parsed);
        assertEquals("student@univ.edu", parsed.getUsername());
        assertEquals("p@ss:/?#%1", parsed.getPassword());
        assertEquals("jdbc:mysql://db.railway.internal:3306/railway?useSSL=false", parsed.getCleanJdbcUrl());
        assertFalse(parsed.getCleanJdbcUrl().contains("student"));
        assertFalse(parsed.getCleanJdbcUrl().contains("p@ss"));
    }

    @Test
    @DisplayName("URL Parser: handles passwords with unencoded colons and @ symbols via fallback parser")
    void testParseDatabaseUrlUnencodedSpecialChars() {
        String raw = "mysql://appuser:p@ss:word_123@junction.proxy.rlwy.net:12345/railway";
        DBConnection.ParsedDatabaseUrl parsed = DBConnection.parseDatabaseUrl(raw);

        assertNotNull(parsed);
        assertEquals("appuser", parsed.getUsername());
        assertEquals("p@ss:word_123", parsed.getPassword());
        assertEquals("jdbc:mysql://junction.proxy.rlwy.net:12345/railway", parsed.getCleanJdbcUrl());
    }

    @Test
    @DisplayName("URL Parser: safeUrlDecode handles lone percent characters without crashing")
    void testSafeUrlDecodeWithLonePercent() {
        // Lone '%' should not throw IllegalArgumentException
        assertEquals("pass%word", DBConnection.safeUrlDecode("pass%word"));
        assertEquals("pass%", DBConnection.safeUrlDecode("pass%"));
        assertEquals("pass%4", DBConnection.safeUrlDecode("pass%4"));
        assertEquals("p@ss", DBConnection.safeUrlDecode("p%40ss"));
    }

    @Test
    @DisplayName("URL Parser: parses standard Railway MySQL URL and extracts credentials")
    void testParseRailwayMySQLUrl() {
        String railwayUrl = "mysql://root:s3cr3tCloudPass@junction.proxy.rlwy.net:12345/railway";
        DBConnection.ParsedDatabaseUrl parsed = DBConnection.parseDatabaseUrl(railwayUrl);

        assertNotNull(parsed);
        assertEquals("root", parsed.getUsername());
        assertEquals("s3cr3tCloudPass", parsed.getPassword());
        assertEquals("jdbc:mysql://junction.proxy.rlwy.net:12345/railway", parsed.getCleanJdbcUrl());
    }

    @Test
    @DisplayName("URL Parser: preserves clean JDBC URLs without embedded credentials")
    void testParseDatabaseUrlCleanJdbc() {
        String cleanJdbc = "jdbc:mysql://localhost:3306/book_exchange_db?useSSL=false&serverTimezone=UTC";
        DBConnection.ParsedDatabaseUrl parsed = DBConnection.parseDatabaseUrl(cleanJdbc);

        assertNotNull(parsed);
        assertNull(parsed.getUsername());
        assertNull(parsed.getPassword());
        assertEquals(cleanJdbc, parsed.getCleanJdbcUrl());
    }

    @Test
    @DisplayName("Credential Resolver: handles conflicting and missing credentials consistently")
    void testResolveCredentialPrecedence() {
        // Case 1: Explicit env var takes precedence over embedded URL credential (conflicting credentials)
        assertEquals("explicit_user", DBConnection.resolveCredential("explicit_user", "railway_user", "url_user"));

        // Case 2: Missing explicit env var falls back to railway user
        assertEquals("railway_user", DBConnection.resolveCredential(null, "railway_user", "url_user"));
        assertEquals("railway_user", DBConnection.resolveCredential("", "railway_user", "url_user"));
        assertEquals("railway_user", DBConnection.resolveCredential("   ", "railway_user", "url_user"));

        // Case 3: Missing explicit and railway env vars fall back to URL credential
        assertEquals("url_user", DBConnection.resolveCredential(null, null, "url_user"));
        assertEquals("url_user", DBConnection.resolveCredential("", "", "url_user"));

        // Case 4: Completely missing returns empty string
        assertEquals("", DBConnection.resolveCredential(null, null, null));
        assertEquals("", DBConnection.resolveCredential("", "  ", ""));
    }

    @Test
    @DisplayName("Validation: valid production configuration passes")
    void testValidationValidProduction() {
        DBConnection.ValidationResult result = DBConnection.validateConfig(
                "jdbc:mysql://mysql.railway.internal:3306/railway",
                "root",
                "secureCloudPass123",
                true
        );
        assertTrue(result.isValid());
        assertNull(result.getError());
    }

    @Test
    @DisplayName("Validation: rejects localhost and loopback URLs in production environment")
    void testValidationRejectsLocalhostInProduction() {
        DBConnection.ValidationResult res1 = DBConnection.validateConfig(
                "jdbc:mysql://localhost:3306/book_exchange_db",
                "root",
                "anyPass",
                true
        );
        assertFalse(res1.isValid());
        assertTrue(res1.getError().contains("localhost"));

        DBConnection.ValidationResult res2 = DBConnection.validateConfig(
                "jdbc:mysql://127.0.0.1:3306/book_exchange_db",
                "root",
                "anyPass",
                true
        );
        assertFalse(res2.isValid());
        assertTrue(res2.getError().contains("localhost") || res2.getError().contains("127.0.0.1"));
    }

    @Test
    @DisplayName("Validation: requires password in production environment")
    void testValidationRequiresPasswordInProduction() {
        DBConnection.ValidationResult resEmpty = DBConnection.validateConfig(
                "jdbc:mysql://mysql.railway.internal:3306/railway",
                "root",
                "",
                true
        );
        assertFalse(resEmpty.isValid());
        assertTrue(resEmpty.getError().contains("password"));

        DBConnection.ValidationResult resNull = DBConnection.validateConfig(
                "jdbc:mysql://mysql.railway.internal:3306/railway",
                "root",
                null,
                true
        );
        assertFalse(resNull.isValid());
        assertTrue(resNull.getError().contains("password"));
    }

    @Test
    @DisplayName("Validation: rejects missing or blank URL")
    void testValidationRejectsMissingUrl() {
        DBConnection.ValidationResult resNull = DBConnection.validateConfig(null, "root", "pass", false);
        assertFalse(resNull.isValid());
        assertTrue(resNull.getError().contains("URL"));

        DBConnection.ValidationResult resBlank = DBConnection.validateConfig("   ", "root", "pass", false);
        assertFalse(resBlank.isValid());
        assertTrue(resBlank.getError().contains("URL"));
    }

    @Test
    @DisplayName("Validation: rejects missing or blank username")
    void testValidationRejectsMissingUsername() {
        DBConnection.ValidationResult resNull = DBConnection.validateConfig(
                "jdbc:mysql://localhost:3306/db",
                null,
                "pass",
                false
        );
        assertFalse(resNull.isValid());
        assertTrue(resNull.getError().contains("username"));

        DBConnection.ValidationResult resBlank = DBConnection.validateConfig(
                "jdbc:mysql://localhost:3306/db",
                "  ",
                "pass",
                false
        );
        assertFalse(resBlank.isValid());
        assertTrue(resBlank.getError().contains("username"));
    }

    @Test
    @DisplayName("Validation: valid local development configuration passes")
    void testValidationValidLocal() {
        DBConnection.ValidationResult result = DBConnection.validateConfig(
                "jdbc:mysql://localhost:3306/book_exchange_db",
                "root",
                "localdevpass",
                false
        );
        assertTrue(result.isValid());
        assertNull(result.getError());
    }

    @Test
    @DisplayName("Error Logging: formatSqlException formats technical details while sanitizing sensitive credentials")
    void testFormatSqlException() {
        SQLException sqlEx = new SQLException(
                "Access denied for user 'root'@'host' (using password: YES) on jdbc:mysql://admin:secret123@db.railway.net:3306/db",
                "28000",
                1045
        );

        String formatted = DBConnection.formatSqlException("TestContext", sqlEx);
        assertNotNull(formatted);
        assertTrue(formatted.contains("[TestContext]"));
        assertTrue(formatted.contains("SQLState=28000"));
        assertTrue(formatted.contains("ErrorCode=1045"));
        assertFalse(formatted.contains("secret123"), "Password must not leak into formatted exception log");
        assertTrue(formatted.contains("***:***@db.railway.net:3306/db"));
    }

    @Test
    @DisplayName("Test Isolation: testConnectionOverride operates with complete thread isolation")
    void testConnectionOverrideThreadIsolation() throws Exception {
        DBConnection.setTestConnectionOverride(true);
        assertTrue(DBConnection.testConnection(), "Main thread override should be true");

        AtomicBoolean childThreadObservedOverride = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        Thread backgroundThread = new Thread(() -> {
            // Child thread must NOT see the main thread's ThreadLocal override
            DBConnection.setTestConnectionOverride(false);
            if (!DBConnection.testConnection()) {
                childThreadObservedOverride.set(true);
            }
            DBConnection.resetTestConnectionOverride();
            latch.countDown();
        });

        backgroundThread.start();
        latch.await();

        assertTrue(childThreadObservedOverride.get(), "Child thread must maintain its own isolated override");
        assertTrue(DBConnection.testConnection(), "Main thread override must remain unaffected by child thread");

        DBConnection.resetTestConnectionOverride();
    }
}
