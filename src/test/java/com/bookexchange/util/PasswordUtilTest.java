package com.bookexchange.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================================
 * PasswordUtilTest - JUnit 5 Tests for PBKDF2 Cryptographic Security
 * ============================================================================
 * Tests:
 * 1. PBKDF2WithHmacSHA256 generation and string formatting.
 * 2. Unique salt verification: Same password produces distinct hashes.
 * 3. Successful verification with correct password.
 * 4. Failure on incorrect password or malformed hash.
 * 5. Backwards-compatibility verification for Phase 1 legacy SHA-256 seed data.
 * ============================================================================
 */
public class PasswordUtilTest {

    @Test
    @DisplayName("Should generate formatted PBKDF2 hash with 65,536 iterations and unique salt")
    void testHashPasswordFormat() {
        String rawPassword = "StudentSecret@2026";
        String hash = PasswordUtil.hashPassword(rawPassword);

        assertNotNull(hash);
        assertTrue(hash.startsWith("PBKDF2:65536:"), "Hash must begin with algorithm prefix and iteration count");

        String[] parts = hash.split(":");
        assertEquals(4, parts.length, "Hash must contain exactly 4 delimited components");
        assertEquals(32, parts[2].length(), "16-byte salt must be 32 hexadecimal characters");
        assertEquals(64, parts[3].length(), "256-bit derived key must be 64 hexadecimal characters");
    }

    @Test
    @DisplayName("Should generate unique salts so identical passwords produce different hashes")
    void testUniqueSaltsForSamePassword() {
        String rawPassword = "CampusPassword@123";

        String hash1 = PasswordUtil.hashPassword(rawPassword);
        String hash2 = PasswordUtil.hashPassword(rawPassword);

        assertNotEquals(hash1, hash2, "Hashes of the same password must differ due to unique salts");

        // But both must successfully verify with the original password
        assertTrue(PasswordUtil.verifyPassword(rawPassword, hash1));
        assertTrue(PasswordUtil.verifyPassword(rawPassword, hash2));
    }

    @Test
    @DisplayName("Should verify valid password and reject incorrect password")
    void testVerifyPassword() {
        String rawPassword = "SecurePassword#99";
        String storedHash = PasswordUtil.hashPassword(rawPassword);

        assertTrue(PasswordUtil.verifyPassword(rawPassword, storedHash), "Correct password must verify as true");
        assertFalse(PasswordUtil.verifyPassword("WrongPassword#99", storedHash), "Incorrect password must verify as false");
        assertFalse(PasswordUtil.verifyPassword("", storedHash), "Empty password must verify as false");
        assertFalse(PasswordUtil.verifyPassword(null, storedHash), "Null password must verify as false");
    }

    @Test
    @DisplayName("Should verify Phase 1 legacy SHA-256 seed password for backward compatibility")
    void testLegacySha256Verification() {
        // Known legacy SHA-256 hash for password '123'
        String legacyHash = "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3";

        assertTrue(PasswordUtil.verifyPassword("123", legacyHash),
                "Legacy SHA-256 hash must verify correctly for existing seed students");
        assertFalse(PasswordUtil.verifyPassword("IncorrectPassword", legacyHash),
                "Legacy verifier must reject wrong password");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when hashing null or empty password")
    void testHashNullOrEmpty() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hashPassword(null));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hashPassword("   "));
    }
}
