package com.bookexchange.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DBConnectionTest {

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
    @DisplayName("URL parser extracts embedded credentials and builds clean JDBC URL")
    void testParseDatabaseUrl() {
        DBConnection.ParsedDatabaseUrl parsed = DBConnection.parseDatabaseUrl(
                "mysql://app_user:secret_token@junction.proxy.rlwy.net:12345/railway?useSSL=false"
        );
        assertNotNull(parsed);
        assertEquals("app_user", parsed.getUsername());
        assertEquals("secret_token", parsed.getPassword());
        assertEquals("jdbc:mysql://junction.proxy.rlwy.net:12345/railway?useSSL=false", parsed.getCleanJdbcUrl());
    }

    @Test
    @DisplayName("URL parser preserves clean JDBC URLs without embedded credentials")
    void testParseDatabaseUrlClean() {
        DBConnection.ParsedDatabaseUrl parsed = DBConnection.parseDatabaseUrl(
                "jdbc:mysql://localhost:3306/book_exchange_db?useSSL=false"
        );
        assertNotNull(parsed);
        assertNull(parsed.getUsername());
        assertNull(parsed.getPassword());
        assertEquals("jdbc:mysql://localhost:3306/book_exchange_db?useSSL=false", parsed.getCleanJdbcUrl());
    }

    @Test
    @DisplayName("Credential resolver picks first non-empty candidate")
    void testResolveCredential() {
        assertEquals("primary", DBConnection.resolveCredential("primary", "secondary"));
        assertEquals("secondary", DBConnection.resolveCredential("", "secondary"));
        assertEquals("secondary", DBConnection.resolveCredential(null, "secondary", "tertiary"));
        assertEquals("tertiary", DBConnection.resolveCredential(null, "  ", "tertiary"));
        assertEquals("", DBConnection.resolveCredential(null, "  ", ""));
    }
}
