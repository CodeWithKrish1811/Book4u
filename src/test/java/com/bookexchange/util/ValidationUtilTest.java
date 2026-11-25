package com.bookexchange.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValidationUtil Comprehensive Unit Tests")
class ValidationUtilTest {

    @Test
    @DisplayName("isNotEmpty & isNotBlank: validate non-null and non-whitespace strings")
    void testIsNotEmptyAndIsNotBlank() {
        assertFalse(ValidationUtil.isNotEmpty(null));
        assertFalse(ValidationUtil.isNotEmpty(""));
        assertFalse(ValidationUtil.isNotEmpty("   "));
        assertFalse(ValidationUtil.isNotEmpty("\t\n"));

        assertTrue(ValidationUtil.isNotEmpty("a"));
        assertTrue(ValidationUtil.isNotEmpty("  hello  "));

        assertFalse(ValidationUtil.isNotBlank(null));
        assertFalse(ValidationUtil.isNotBlank("   "));
        assertTrue(ValidationUtil.isNotBlank("valid"));
    }

    @Test
    @DisplayName("isValidEmail: validate institutional and standard emails")
    void testIsValidEmail() {
        assertTrue(ValidationUtil.isValidEmail("student@campus.edu"));
        assertTrue(ValidationUtil.isValidEmail("john.doe@university.ac.in"));
        assertTrue(ValidationUtil.isValidEmail("user123@example.com"));

        assertFalse(ValidationUtil.isValidEmail(null));
        assertFalse(ValidationUtil.isValidEmail(""));
        assertFalse(ValidationUtil.isValidEmail("   "));
        assertFalse(ValidationUtil.isValidEmail("not-an-email"));
        assertFalse(ValidationUtil.isValidEmail("missing@domain"));
        assertFalse(ValidationUtil.isValidEmail("@missingusername.com"));
        assertFalse(ValidationUtil.isValidEmail("spaces in@email.com"));

        // Max length check (> 255 chars)
        String longEmail = "a".repeat(250) + "@test.com";
        assertFalse(ValidationUtil.isValidEmail(longEmail));
    }

    @Test
    @DisplayName("isValidSharingMode & isValidExchangeMode: validate SWAP, GIVEAWAY, LENDING")
    void testIsValidSharingMode() {
        assertTrue(ValidationUtil.isValidSharingMode("SWAP"));
        assertTrue(ValidationUtil.isValidSharingMode("GIVEAWAY"));
        assertTrue(ValidationUtil.isValidSharingMode("LENDING"));
        assertTrue(ValidationUtil.isValidSharingMode("swap"));
        assertTrue(ValidationUtil.isValidSharingMode("  lending  "));

        assertFalse(ValidationUtil.isValidSharingMode(null));
        assertFalse(ValidationUtil.isValidSharingMode(""));
        assertFalse(ValidationUtil.isValidSharingMode("SELL"));
        assertFalse(ValidationUtil.isValidSharingMode("RENT"));

        assertTrue(ValidationUtil.isValidExchangeMode("SWAP"));
        assertFalse(ValidationUtil.isValidExchangeMode("INVALID"));
    }

    @Test
    @DisplayName("isValidConditionGrade: validate NEW, LIKE_NEW, GOOD, FAIR")
    void testIsValidConditionGrade() {
        assertTrue(ValidationUtil.isValidConditionGrade("NEW"));
        assertTrue(ValidationUtil.isValidConditionGrade("LIKE_NEW"));
        assertTrue(ValidationUtil.isValidConditionGrade("GOOD"));
        assertTrue(ValidationUtil.isValidConditionGrade("FAIR"));
        assertTrue(ValidationUtil.isValidConditionGrade("good"));
        assertTrue(ValidationUtil.isValidConditionGrade(" like_new "));

        assertFalse(ValidationUtil.isValidConditionGrade(null));
        assertFalse(ValidationUtil.isValidConditionGrade(""));
        assertFalse(ValidationUtil.isValidConditionGrade("POOR"));
        assertFalse(ValidationUtil.isValidConditionGrade("EXCELLENT"));
    }

    @Test
    @DisplayName("isValidBookStatus: validate AVAILABLE, RESERVED, ON_LOAN, UNAVAILABLE")
    void testIsValidBookStatus() {
        assertTrue(ValidationUtil.isValidBookStatus("AVAILABLE"));
        assertTrue(ValidationUtil.isValidBookStatus("RESERVED"));
        assertTrue(ValidationUtil.isValidBookStatus("ON_LOAN"));
        assertTrue(ValidationUtil.isValidBookStatus("UNAVAILABLE"));
        assertTrue(ValidationUtil.isValidBookStatus("available"));

        assertFalse(ValidationUtil.isValidBookStatus(null));
        assertFalse(ValidationUtil.isValidBookStatus(""));
        assertFalse(ValidationUtil.isValidBookStatus("SOLD"));
        assertFalse(ValidationUtil.isValidBookStatus("DELETED"));
    }

    @Test
    @DisplayName("isValidTitle: validate non-blank and length <= 255")
    void testIsValidTitle() {
        assertTrue(ValidationUtil.isValidTitle("Database System Concepts"));
        assertTrue(ValidationUtil.isValidTitle("A"));

        assertFalse(ValidationUtil.isValidTitle(null));
        assertFalse(ValidationUtil.isValidTitle(""));
        assertFalse(ValidationUtil.isValidTitle("   "));
        assertFalse(ValidationUtil.isValidTitle("A".repeat(256)));
        assertTrue(ValidationUtil.isValidTitle("A".repeat(255)));
    }

    @Test
    @DisplayName("isValidAuthor: validate non-blank and length <= 255")
    void testIsValidAuthor() {
        assertTrue(ValidationUtil.isValidAuthor("Abraham Silberschatz"));

        assertFalse(ValidationUtil.isValidAuthor(null));
        assertFalse(ValidationUtil.isValidAuthor(""));
        assertFalse(ValidationUtil.isValidAuthor("   "));
        assertFalse(ValidationUtil.isValidAuthor("B".repeat(256)));
        assertTrue(ValidationUtil.isValidAuthor("B".repeat(255)));
    }

    @Test
    @DisplayName("isValidSubject: validate non-blank and length <= 128")
    void testIsValidSubject() {
        assertTrue(ValidationUtil.isValidSubject("Computer Science"));

        assertFalse(ValidationUtil.isValidSubject(null));
        assertFalse(ValidationUtil.isValidSubject(""));
        assertFalse(ValidationUtil.isValidSubject("   "));
        assertFalse(ValidationUtil.isValidSubject("C".repeat(129)));
        assertTrue(ValidationUtil.isValidSubject("C".repeat(128)));
    }

    @Test
    @DisplayName("isValidIsbn & isValidOptionalIsbn: validate 10- and 13-digit ISBNs")
    void testIsValidIsbn() {
        // ISBN-13
        assertTrue(ValidationUtil.isValidIsbn("9780134685991"));
        assertTrue(ValidationUtil.isValidIsbn("978-0-13-468599-1"));
        assertTrue(ValidationUtil.isValidIsbn("978 0 13 468599 1"));
        assertTrue(ValidationUtil.isValidIsbn("9791234567890"));

        // ISBN-10
        assertTrue(ValidationUtil.isValidIsbn("0262033844"));
        assertTrue(ValidationUtil.isValidIsbn("0-262-03384-4"));
        assertTrue(ValidationUtil.isValidIsbn("0134494164"));
        assertTrue(ValidationUtil.isValidIsbn("098478285X"));
        assertTrue(ValidationUtil.isValidIsbn("0-984-78285-x"));

        // Invalid ISBNs
        assertFalse(ValidationUtil.isValidIsbn(null));
        assertFalse(ValidationUtil.isValidIsbn(""));
        assertFalse(ValidationUtil.isValidIsbn("   "));
        assertFalse(ValidationUtil.isValidIsbn("12345"));
        assertFalse(ValidationUtil.isValidIsbn("978013468599112345")); // too long
        assertFalse(ValidationUtil.isValidIsbn("abcdefghij"));
        assertFalse(ValidationUtil.isValidIsbn("X123456789")); // X only allowed at end of ISBN-10

        // Optional ISBN
        assertTrue(ValidationUtil.isValidOptionalIsbn(null));
        assertTrue(ValidationUtil.isValidOptionalIsbn(""));
        assertTrue(ValidationUtil.isValidOptionalIsbn("   "));
        assertTrue(ValidationUtil.isValidOptionalIsbn("9780134685991"));
        assertFalse(ValidationUtil.isValidOptionalIsbn("invalid-isbn"));
    }

    @Test
    @DisplayName("isValidDescription: validate optional text length <= 4000")
    void testIsValidDescription() {
        assertTrue(ValidationUtil.isValidDescription(null));
        assertTrue(ValidationUtil.isValidDescription(""));
        assertTrue(ValidationUtil.isValidDescription("A good textbook with notes"));
        assertTrue(ValidationUtil.isValidDescription("D".repeat(4000)));

        assertFalse(ValidationUtil.isValidDescription("D".repeat(4001)));
    }

    @Test
    @DisplayName("isValidEdition & isValidCourseCode & isValidImageUrl: boundary length checks")
    void testAuxiliaryBookFields() {
        assertTrue(ValidationUtil.isValidEdition(null));
        assertTrue(ValidationUtil.isValidEdition("3rd Edition"));
        assertTrue(ValidationUtil.isValidEdition("E".repeat(32)));
        assertFalse(ValidationUtil.isValidEdition("E".repeat(33)));

        assertTrue(ValidationUtil.isValidCourseCode(null));
        assertTrue(ValidationUtil.isValidCourseCode("CS301"));
        assertTrue(ValidationUtil.isValidCourseCode("C".repeat(32)));
        assertFalse(ValidationUtil.isValidCourseCode("C".repeat(33)));

        assertTrue(ValidationUtil.isValidImageUrl(null));
        assertTrue(ValidationUtil.isValidImageUrl("https://example.com/cover.jpg"));
        assertTrue(ValidationUtil.isValidImageUrl("U".repeat(512)));
        assertFalse(ValidationUtil.isValidImageUrl("U".repeat(513)));

        assertTrue(ValidationUtil.isValidSwapPreferences(null));
        assertTrue(ValidationUtil.isValidSwapPreferences("Looking for OS book"));
        assertTrue(ValidationUtil.isValidSwapPreferences("S".repeat(2000)));
        assertFalse(ValidationUtil.isValidSwapPreferences("S".repeat(2001)));
    }

    @Test
    @DisplayName("isValidLendingDuration: positive and <= 180 days")
    void testIsValidLendingDuration() {
        assertTrue(ValidationUtil.isValidLendingDuration(1));
        assertTrue(ValidationUtil.isValidLendingDuration(30));
        assertTrue(ValidationUtil.isValidLendingDuration(180));

        assertFalse(ValidationUtil.isValidLendingDuration(null));
        assertFalse(ValidationUtil.isValidLendingDuration(0));
        assertFalse(ValidationUtil.isValidLendingDuration(-10));
        assertFalse(ValidationUtil.isValidLendingDuration(181));
    }

    @Test
    @DisplayName("isValidSemester: academic semester between 1 and 12")
    void testIsValidSemester() {
        assertTrue(ValidationUtil.isValidSemester(1));
        assertTrue(ValidationUtil.isValidSemester(6));
        assertTrue(ValidationUtil.isValidSemester(12));

        assertFalse(ValidationUtil.isValidSemester(0));
        assertFalse(ValidationUtil.isValidSemester(-1));
        assertFalse(ValidationUtil.isValidSemester(13));
    }

    @Test
    @DisplayName("isValidUniversityId & isValidFullName & isValidDepartment: length boundaries")
    void testUserFields() {
        assertTrue(ValidationUtil.isValidUniversityId("CS-2024-001"));
        assertFalse(ValidationUtil.isValidUniversityId(null));
        assertFalse(ValidationUtil.isValidUniversityId(""));
        assertFalse(ValidationUtil.isValidUniversityId("U".repeat(65)));

        assertTrue(ValidationUtil.isValidFullName("Aarav Sharma"));
        assertFalse(ValidationUtil.isValidFullName(null));
        assertFalse(ValidationUtil.isValidFullName(""));
        assertFalse(ValidationUtil.isValidFullName("N".repeat(129)));

        assertTrue(ValidationUtil.isValidDepartment("Computer Science"));
        assertFalse(ValidationUtil.isValidDepartment(null));
        assertFalse(ValidationUtil.isValidDepartment(""));
        assertFalse(ValidationUtil.isValidDepartment("D".repeat(129)));

        assertTrue(ValidationUtil.isValidCampusName("Main Campus"));
        assertFalse(ValidationUtil.isValidCampusName(null));
        assertFalse(ValidationUtil.isValidCampusName(""));
        assertFalse(ValidationUtil.isValidCampusName("C".repeat(129)));
    }

    @Test
    @DisplayName("isValidPhone: optional phone format and length")
    void testIsValidPhone() {
        assertTrue(ValidationUtil.isValidPhone(null));
        assertTrue(ValidationUtil.isValidPhone(""));
        assertTrue(ValidationUtil.isValidPhone("   "));
        assertTrue(ValidationUtil.isValidPhone("+91-9876543210"));
        assertTrue(ValidationUtil.isValidPhone("9876543210"));
        assertTrue(ValidationUtil.isValidPhone("+1 (555) 123-4567"));

        assertFalse(ValidationUtil.isValidPhone("123")); // too short
        assertFalse(ValidationUtil.isValidPhone("not-a-phone-number"));
        assertFalse(ValidationUtil.isValidPhone("+123456789012345678901")); // > 20 chars
    }

    @Test
    @DisplayName("isValidPassword: length between 6 and 128 characters")
    void testIsValidPassword() {
        assertTrue(ValidationUtil.isValidPassword("secret123"));
        assertTrue(ValidationUtil.isValidPassword("123456"));
        assertTrue(ValidationUtil.isValidPassword("P".repeat(128)));

        assertFalse(ValidationUtil.isValidPassword(null));
        assertFalse(ValidationUtil.isValidPassword(""));
        assertFalse(ValidationUtil.isValidPassword("12345")); // too short
        assertFalse(ValidationUtil.isValidPassword("P".repeat(129))); // too long
    }

    @Test
    @DisplayName("isValidPositiveId: Long and String positive numeric validation")
    void testIsValidPositiveId() {
        assertTrue(ValidationUtil.isValidPositiveId(1L));
        assertTrue(ValidationUtil.isValidPositiveId(999L));
        assertFalse(ValidationUtil.isValidPositiveId((Long) null));
        assertFalse(ValidationUtil.isValidPositiveId(0L));
        assertFalse(ValidationUtil.isValidPositiveId(-5L));

        assertTrue(ValidationUtil.isValidPositiveId("1"));
        assertTrue(ValidationUtil.isValidPositiveId(" 42 "));
        assertFalse(ValidationUtil.isValidPositiveId((String) null));
        assertFalse(ValidationUtil.isValidPositiveId(""));
        assertFalse(ValidationUtil.isValidPositiveId("   "));
        assertFalse(ValidationUtil.isValidPositiveId("0"));
        assertFalse(ValidationUtil.isValidPositiveId("-10"));
        assertFalse(ValidationUtil.isValidPositiveId("abc"));
    }

    @Test
    @DisplayName("isValidHandoverLocation: optional max 255 chars")
    void testIsValidHandoverLocation() {
        assertTrue(ValidationUtil.isValidHandoverLocation(null));
        assertTrue(ValidationUtil.isValidHandoverLocation(""));
        assertTrue(ValidationUtil.isValidHandoverLocation("Central Library 2nd Floor"));
        assertTrue(ValidationUtil.isValidHandoverLocation("L".repeat(255)));

        assertFalse(ValidationUtil.isValidHandoverLocation("L".repeat(256)));
    }
}
