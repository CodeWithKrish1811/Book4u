package com.bookexchange.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

/**
 * ============================================================================
 * PasswordUtil - Cryptographic Password Hashing Utility
 * ============================================================================
 * University Academic Project: Student Peer-to-Peer Book Exchange System
 * Technology: Java 21 Security & Crypto API (PBKDF2WithHmacSHA256)
 *
 * Security Concepts Demonstrated:
 * 1. PBKDF2 (Password-Based Key Derivation Function 2):
 * Recommended by NIST / OWASP for password storage. Applies HMAC-SHA256
 * repeatedly (default 65,536 iterations) to slow down brute-force attacks.
 * 2. Cryptographic Salt:
 * Each password gets a unique, cryptographically random 16-byte salt from
 * SecureRandom. This neutralizes Rainbow Table attacks.
 * 3. Constant-Time Verification:
 * Uses MessageDigest.isEqual() to eliminate timing attack vulnerabilities.
 * 4. Backward Compatibility:
 * Supports verifying legacy Phase 1 SHA-256 seed passwords alongside PBKDF2.
 * ============================================================================
 */
public class PasswordUtil {

    // PBKDF2 Configuration Parameters
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16; // 128-bit salt
    private static final int ITERATIONS = 65536; // Recommended iteration count
    private static final int KEY_LENGTH_BITS = 256; // 256-bit derived key length
    private static final String PREFIX = "PBKDF2";

    // Legacy Phase 1 salt for backwards compatibility with initial seed data
    private static final String LEGACY_SALT = "Book4uCampusP2PSalt_2026!";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordUtil() {
        // Private constructor prevents instantiation
    }

    /**
     * Hashes a plaintext password using PBKDF2WithHmacSHA256 with a unique salt.
     *
     * @param rawPassword The plaintext password entered by the student
     * @return Formatted hash string: PBKDF2:<iterations>:<saltHex>:<hashHex>
     */
    public static String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }

        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);

        byte[] hash = computePBKDF2(rawPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);

        String saltHex = bytesToHex(salt);
        String hashHex = bytesToHex(hash);

        return PREFIX + ":" + ITERATIONS + ":" + saltHex + ":" + hashHex;
    }

    /**
     * Verifies if a candidate plaintext password matches a stored hash string.
     * Handles both PBKDF2 hashes and legacy SHA-256 hashes seamlessly.
     *
     * @param rawPassword The plaintext candidate password
     * @param storedHash  The stored password hash from the database
     * @return true if password matches, false otherwise
     */
    public static boolean verifyPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.trim().isEmpty()) {
            return false;
        }

        // Check if stored hash is formatted with PBKDF2
        if (storedHash.startsWith(PREFIX + ":")) {
            String[] parts = storedHash.split(":");
            if (parts.length != 4) {
                return false;
            }

            try {
                int iterations = Integer.parseInt(parts[1]);
                byte[] salt = hexToBytes(parts[2]);
                byte[] expectedHash = hexToBytes(parts[3]);

                byte[] candidateHash = computePBKDF2(rawPassword.toCharArray(), salt, iterations,
                        expectedHash.length * 8);

                // Constant-time byte array comparison against timing attacks
                return MessageDigest.isEqual(candidateHash, expectedHash);

            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        // Fallback: Verify legacy Phase 1 SHA-256 hash
        return verifyLegacySha256(rawPassword, storedHash);
    }

    /**
     * Computes the PBKDF2 hash using SecretKeyFactory.
     */
    private static byte[] computePBKDF2(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        try {
            KeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Cryptographic error while computing PBKDF2: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies against legacy SHA-256 (both raw seed hash and Phase 1 salted
     * format).
     */
    private static boolean verifyLegacySha256(String rawPassword, String storedHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Check raw SHA-256 (matches SQL seed data)
            byte[] rawHash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            if (bytesToHex(rawHash).equalsIgnoreCase(storedHash.trim())) {
                return true;
            }

            // Also check salted SHA-256 (from Phase 1 PasswordUtil)
            digest.reset();
            byte[] saltedHash = digest.digest((rawPassword + LEGACY_SALT).getBytes(StandardCharsets.UTF_8));
            return bytesToHex(saltedHash).equalsIgnoreCase(storedHash.trim());
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    /**
     * Converts a byte array to a lowercase hexadecimal string.
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Converts a hexadecimal string back to a byte array.
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hexadecimal string length");
        }
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
