package com.bookexchange.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * ============================================================================
 * ValidationUtil - Input Validation Utility
 * ============================================================================
 * Centralizes input sanity, boundary checks, and business validation rules.
 * Demonstrates: Regular expressions, length limits, clean defense against
 * null values, empty strings, and malformed requests across the application.
 * ============================================================================
 */
public class ValidationUtil {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^[+]?[0-9\\s().-]{7,20}$"
    );

    private static final Pattern ISBN10_PATTERN = Pattern.compile(
            "^[0-9]{9}[0-9X]$"
    );

    private static final Pattern ISBN13_PATTERN = Pattern.compile(
            "^(978|979)[0-9]{10}$"
    );

    private ValidationUtil() {
    }

    /**
     * Validates if a string is non-null and not blank (contains non-whitespace characters).
     */
    public static boolean isNotEmpty(String str) {
        return str != null && !str.trim().isEmpty();
    }

    /**
     * Alias for isNotEmpty to support conventional naming.
     */
    public static boolean isNotBlank(String str) {
        return isNotEmpty(str);
    }

    /**
     * Validates an institutional or standard email format with max length check.
     */
    public static boolean isValidEmail(String email) {
        if (!isNotEmpty(email)) {
            return false;
        }
        String trimmed = email.trim();
        if (trimmed.length() > 255) {
            return false;
        }
        return EMAIL_PATTERN.matcher(trimmed).matches();
    }

    /**
     * Validates academic book sharing modes.
     * Allowed: SWAP, GIVEAWAY, LENDING.
     */
    public static boolean isValidSharingMode(String mode) {
        if (!isNotEmpty(mode)) {
            return false;
        }
        String upper = mode.trim().toUpperCase(Locale.ROOT);
        return "SWAP".equals(upper) || "GIVEAWAY".equals(upper) || "LENDING".equals(upper);
    }

    /**
     * Alias for isValidSharingMode.
     */
    public static boolean isValidExchangeMode(String mode) {
        return isValidSharingMode(mode);
    }

    /**
     * Validates physical condition grades for books.
     * Allowed: NEW, LIKE_NEW, GOOD, FAIR.
     */
    public static boolean isValidConditionGrade(String grade) {
        if (!isNotEmpty(grade)) {
            return false;
        }
        String upper = grade.trim().toUpperCase(Locale.ROOT);
        return "NEW".equals(upper) || "LIKE_NEW".equals(upper) || "GOOD".equals(upper) || "FAIR".equals(upper);
    }

    /**
     * Validates book listing lifecycle status.
     * Allowed: AVAILABLE, RESERVED, ON_LOAN, UNAVAILABLE.
     */
    public static boolean isValidBookStatus(String status) {
        if (!isNotEmpty(status)) {
            return false;
        }
        String upper = status.trim().toUpperCase(Locale.ROOT);
        return "AVAILABLE".equals(upper) || "RESERVED".equals(upper) || "ON_LOAN".equals(upper) || "UNAVAILABLE".equals(upper);
    }

    /**
     * Validates book title: non-empty, max 255 characters.
     */
    public static boolean isValidTitle(String title) {
        return isNotEmpty(title) && title.trim().length() <= 255;
    }

    /**
     * Validates author name: non-empty, max 255 characters.
     */
    public static boolean isValidAuthor(String author) {
        return isNotEmpty(author) && author.trim().length() <= 255;
    }

    /**
     * Validates academic subject: non-empty, max 128 characters.
     */
    public static boolean isValidSubject(String subject) {
        return isNotEmpty(subject) && subject.trim().length() <= 128;
    }

    /**
     * Validates ISBN-10 or ISBN-13 format.
     * Accepts hyphenated, spaced, or plain numeric strings (e.g., 978-0-13-468599-1 or 0134494164).
     */
    public static boolean isValidIsbn(String rawIsbn) {
        if (!isNotEmpty(rawIsbn)) {
            return false;
        }
        String trimmed = rawIsbn.trim();
        if (trimmed.length() > 32) {
            return false;
        }
        String cleaned = trimmed.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (cleaned.length() == 10) {
            return ISBN10_PATTERN.matcher(cleaned).matches();
        } else if (cleaned.length() == 13) {
            return ISBN13_PATTERN.matcher(cleaned).matches() || cleaned.matches("^[0-9]{13}$");
        }
        return false;
    }

    /**
     * Validates optional ISBN (null/blank is allowed, but if present must be valid).
     */
    public static boolean isValidOptionalIsbn(String rawIsbn) {
        return !isNotEmpty(rawIsbn) || isValidIsbn(rawIsbn);
    }

    /**
     * Validates book description (optional, max 4000 characters).
     */
    public static boolean isValidDescription(String description) {
        return description == null || description.trim().length() <= 4000;
    }

    /**
     * Validates edition string (optional, max 32 characters).
     */
    public static boolean isValidEdition(String edition) {
        return edition == null || edition.trim().length() <= 32;
    }

    /**
     * Validates course code (optional, max 32 characters).
     */
    public static boolean isValidCourseCode(String courseCode) {
        return courseCode == null || courseCode.trim().length() <= 32;
    }

    /**
     * Validates image URL (optional, max 512 characters).
     */
    public static boolean isValidImageUrl(String url) {
        return url == null || url.trim().length() <= 512;
    }

    /**
     * Validates swap preferences (optional, max 2000 characters).
     */
    public static boolean isValidSwapPreferences(String swapPreferences) {
        return swapPreferences == null || swapPreferences.trim().length() <= 2000;
    }

    /**
     * Validates lending duration in days: must be positive and not exceed 180 days.
     */
    public static boolean isValidLendingDuration(Integer days) {
        return days != null && days > 0 && days <= 180;
    }

    /**
     * Validates academic semester (must be between 1 and 12).
     */
    public static boolean isValidSemester(int semester) {
        return semester >= 1 && semester <= 12;
    }

    /**
     * Validates university roll number / campus ID: non-empty, max 64 characters.
     */
    public static boolean isValidUniversityId(String universityId) {
        return isNotEmpty(universityId) && universityId.trim().length() <= 64;
    }

    /**
     * Validates student full name: non-empty, max 128 characters.
     */
    public static boolean isValidFullName(String fullName) {
        return isNotEmpty(fullName) && fullName.trim().length() <= 128;
    }

    /**
     * Validates academic department: non-empty, max 128 characters.
     */
    public static boolean isValidDepartment(String department) {
        return isNotEmpty(department) && department.trim().length() <= 128;
    }

    /**
     * Validates campus branch name: non-empty, max 128 characters.
     */
    public static boolean isValidCampusName(String campusName) {
        return isNotEmpty(campusName) && campusName.trim().length() <= 128;
    }

    /**
     * Validates phone number: optional, but if present must be 7-20 chars and match pattern.
     */
    public static boolean isValidPhone(String phone) {
        if (!isNotEmpty(phone)) {
            return true; // Optional field
        }
        String trimmed = phone.trim();
        return trimmed.length() <= 20 && PHONE_PATTERN.matcher(trimmed).matches();
    }

    /**
     * Validates plaintext password: non-null, between 6 and 128 characters.
     */
    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 6 && password.length() <= 128;
    }

    /**
     * Validates a positive database primary key ID.
     */
    public static boolean isValidPositiveId(Long id) {
        return id != null && id > 0;
    }

    /**
     * Validates if a string parameter represents a positive numeric ID.
     */
    public static boolean isValidPositiveId(String idStr) {
        if (!isNotEmpty(idStr)) {
            return false;
        }
        try {
            long id = Long.parseLong(idStr.trim());
            return id > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Validates physical handover location (optional, max 255 characters).
     */
    public static boolean isValidHandoverLocation(String location) {
        return location == null || location.trim().length() <= 255;
    }
}
