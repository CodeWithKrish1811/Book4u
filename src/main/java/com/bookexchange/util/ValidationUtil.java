package com.bookexchange.util;

import java.util.regex.Pattern;

/**
 * ============================================================================
 * ValidationUtil - Input Validation Utility
 * ============================================================================
 * Centralizes input sanity and business validation rules.
 * Demonstrates: Regular expressions, boundary checks, clean defense against malformed inputs.
 * ============================================================================
 */
public class ValidationUtil {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$"
    );

    private ValidationUtil() {
    }

    /**
     * Validates if a string is non-null and not blank.
     */
    public static boolean isNotEmpty(String str) {
        return str != null && !str.trim().isEmpty();
    }

    /**
     * Validates an institutional or standard email format.
     */
    public static boolean isValidEmail(String email) {
        if (!isNotEmpty(email)) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Validates academic book sharing modes.
     * Allowed: SWAP, GIVEAWAY, LENDING.
     */
    public static boolean isValidSharingMode(String mode) {
        if (!isNotEmpty(mode)) {
            return false;
        }
        String upper = mode.trim().toUpperCase();
        return "SWAP".equals(upper) || "GIVEAWAY".equals(upper) || "LENDING".equals(upper);
    }
}
