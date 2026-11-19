package com.bookexchange.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * ============================================================================
 * PasswordUtil - Secure Hashing Utility
 * ============================================================================
 * Demonstrates:
 * 1. Cryptographic hashing using standard Java Security API (MessageDigest).
 * 2. Protection of passwords against plain-text database exposure.
 * 3. Hexadecimal conversion of byte digests.
 * ============================================================================
 */
public class PasswordUtil {

    private static final String SALT = "Book4uCampusP2PSalt_2026!";

    private PasswordUtil() {
    }

    /**
     * Hashes a raw plaintext password using SHA-256 cryptographic algorithm with salt.
     *
     * @param rawPassword The plaintext password entered by the student
     * @return 64-character hexadecimal SHA-256 hash string
     */
    public static String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String saltedInput = rawPassword + SALT;
            byte[] encodedHash = digest.digest(saltedInput.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available in current Java runtime", e);
        }
    }

    /**
     * Verifies if a raw password matches an existing stored SHA-256 hash.
     *
     * @param rawPassword The candidate password
     * @param storedHash The expected hash
     * @return true if the hash matches, false otherwise
     */
    public static boolean verifyPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }
        String candidateHash = hashPassword(rawPassword);
        return candidateHash.equalsIgnoreCase(storedHash);
    }
}
