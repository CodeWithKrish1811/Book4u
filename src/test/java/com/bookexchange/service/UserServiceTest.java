package com.bookexchange.service;

import com.bookexchange.dao.UserDAO;
import com.bookexchange.model.User;
import com.bookexchange.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================================
 * UserServiceTest - Comprehensive Scenario Tests for Phase 2
 * ============================================================================
 * Tests the 11 key scenarios specified in Phase 2:
 * 1. Successful student registration.
 * 2. Duplicate email registration rejection.
 * 3. Duplicate university ID registration rejection.
 * 4. Invalid email format rejection.
 * 5. Password confirmation mismatch rejection.
 * 6. Minimum password length requirement (< 6 chars).
 * 7. Successful login / authentication.
 * 8. Incorrect password authentication failure.
 * 9. Non-existent email authentication failure.
 * 10. Student profile update.
 * 11. Secure password change (verifying old password and updating hash).
 * ============================================================================
 */
public class UserServiceTest {

    private UserService userService;
    private InMemoryUserDAO mockDao;

    @BeforeEach
    void setUp() {
        mockDao = new InMemoryUserDAO();
        userService = new UserService(mockDao);

        // Seed with a registered student
        User seedUser = new User(
                "U2023CS001",
                "Aarav Sharma",
                "aarav.sharma@campus.edu",
                "+919876543210",
                PasswordUtil.hashPassword("Password@123"),
                "Computer Science",
                5,
                "Main Campus"
        );
        seedUser.setId(1L);
        mockDao.saveDirect(seedUser);
    }

    @Test
    @DisplayName("Scenario 1: Successful student registration with PBKDF2 hashing")
    void testSuccessfulRegistration() throws SQLException {
        User newUser = new User(
                "U2024EC099",
                "Maya Desai",
                "maya.desai@campus.edu",
                "+919876543299",
                null,
                "Electronics & Communication",
                3,
                "Main Campus"
        );

        User registered = userService.registerStudent(newUser, "SecurePass@2026", "SecurePass@2026");

        assertNotNull(registered);
        assertNotNull(registered.getId());
        assertEquals("Maya Desai", registered.getFullName());
        assertNull(registered.getPasswordHash(), "Password hash must be cleared from returned object");

        // Verify stored entity in DAO has PBKDF2 hash
        User stored = mockDao.findById(registered.getId());
        assertNotNull(stored);
        assertTrue(stored.getPasswordHash().startsWith("PBKDF2:65536:"));
        assertTrue(PasswordUtil.verifyPassword("SecurePass@2026", stored.getPasswordHash()));
    }

    @Test
    @DisplayName("Scenario 2: Duplicate email registration rejection")
    void testDuplicateEmailRegistration() {
        User duplicateEmailUser = new User(
                "U2024CS555",
                "Different Name",
                "aarav.sharma@campus.edu", // existing email
                null,
                null,
                "Computer Science",
                1,
                "Main Campus"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.registerStudent(duplicateEmailUser, "ValidPass@123", "ValidPass@123")
        );
        assertTrue(ex.getMessage().contains("already exists"), "Must report duplicate email error");
    }

    @Test
    @DisplayName("Scenario 3: Duplicate university ID registration rejection")
    void testDuplicateUniversityIdRegistration() {
        User duplicateUnivIdUser = new User(
                "U2023CS001", // existing university ID
                "Different Name",
                "unique.email@campus.edu",
                null,
                null,
                "Information Science",
                2,
                "Main Campus"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.registerStudent(duplicateUnivIdUser, "ValidPass@123", "ValidPass@123")
        );
        assertTrue(ex.getMessage().contains("already exists"), "Must report duplicate university ID error");
    }

    @Test
    @DisplayName("Scenario 4: Invalid email format rejection")
    void testInvalidEmailRegistration() {
        User invalidEmailUser = new User(
                "U2024CS999",
                "John Doe",
                "invalid-email-no-at-domain",
                null,
                null,
                "Computer Science",
                1,
                "Main Campus"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.registerStudent(invalidEmailUser, "ValidPass@123", "ValidPass@123")
        );
        assertTrue(ex.getMessage().contains("Invalid college email format"));
    }

    @Test
    @DisplayName("Scenario 5: Password confirmation mismatch rejection")
    void testPasswordConfirmationMismatch() {
        User user = new User(
                "U2024CS777",
                "Alice Smith",
                "alice.smith@campus.edu",
                null,
                null,
                "Mechanical Engineering",
                2,
                "Main Campus"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.registerStudent(user, "PasswordA@123", "PasswordB@123")
        );
        assertTrue(ex.getMessage().contains("do not match"));
    }

    @Test
    @DisplayName("Scenario 6: Short password rejection (< 6 characters)")
    void testShortPasswordRejection() {
        User user = new User(
                "U2024CS888",
                "Bob Lee",
                "bob.lee@campus.edu",
                null,
                null,
                "Civil Engineering",
                1,
                "Main Campus"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.registerStudent(user, "12345", "12345")
        );
        assertTrue(ex.getMessage().contains("at least 6 characters"));
    }

    @Test
    @DisplayName("Scenario 7: Successful login with valid email and password")
    void testSuccessfulAuthentication() throws SQLException {
        User student = userService.authenticate("aarav.sharma@campus.edu", "Password@123");

        assertNotNull(student, "Authentication must succeed for matching credentials");
        assertEquals("Aarav Sharma", student.getFullName());
        assertNull(student.getPasswordHash(), "Password hash should not be exposed in session user");
    }

    @Test
    @DisplayName("Scenario 8: Failed login with incorrect password")
    void testFailedAuthenticationWrongPassword() throws SQLException {
        User student = userService.authenticate("aarav.sharma@campus.edu", "WrongPassword!99");
        assertNull(student, "Authentication must return null for incorrect password");
    }

    @Test
    @DisplayName("Scenario 9: Failed login with non-existent email")
    void testFailedAuthenticationNonExistentEmail() throws SQLException {
        User student = userService.authenticate("ghost.student@campus.edu", "Password@123");
        assertNull(student, "Authentication must return null for unregistered email");
    }

    @Test
    @DisplayName("Scenario 10: Permitted profile details update")
    void testProfileUpdate() throws SQLException {
        User updated = userService.updateProfile(
                1L,
                "Aarav Sharma Updated",
                "+919999988888",
                "Computer Science & AI",
                6,
                "North Campus"
        );

        assertNotNull(updated);
        assertEquals("Aarav Sharma Updated", updated.getFullName());
        assertEquals("+919999988888", updated.getPhone());
        assertEquals(6, updated.getSemester());
        assertEquals("North Campus", updated.getCampusName());

        // Verify that universityId and email remained unchanged
        assertEquals("U2023CS001", updated.getUniversityId());
        assertEquals("aarav.sharma@campus.edu", updated.getEmail());
    }

    @Test
    @DisplayName("Scenario 11: Secure password change with verification")
    void testPasswordChangeWorkflow() throws SQLException {
        // Change password from 'Password@123' to 'NewBrandNewPass@456'
        userService.changePassword(1L, "Password@123", "NewBrandNewPass@456", "NewBrandNewPass@456");

        // Old password must fail
        User oldAuth = userService.authenticate("aarav.sharma@campus.edu", "Password@123");
        assertNull(oldAuth, "Old password must no longer authenticate");

        // New password must succeed
        User newAuth = userService.authenticate("aarav.sharma@campus.edu", "NewBrandNewPass@456");
        assertNotNull(newAuth, "New password must authenticate successfully");
    }

    @Test
    @DisplayName("Scenario 12: Password change rejection when current password is wrong")
    void testPasswordChangeWrongCurrent() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.changePassword(1L, "WrongCurrentPass", "NewPassword@123", "NewPassword@123")
        );
        assertTrue(ex.getMessage().contains("Current password entered is incorrect"));
    }

    // ========================================================================
    // In-Memory Test DAO Mock to isolate UserService tests from live database
    // ========================================================================
    private static class InMemoryUserDAO extends UserDAO {
        private final Map<Long, User> storageById = new HashMap<>();
        private final Map<String, User> storageByEmail = new HashMap<>();
        private final Map<String, User> storageByUnivId = new HashMap<>();
        private long idSequence = 100L;

        public void saveDirect(User user) {
            storageById.put(user.getId(), user);
            storageByEmail.put(user.getEmail().toLowerCase(), user);
            storageByUnivId.put(user.getUniversityId().toLowerCase(), user);
        }

        @Override
        public User findByEmail(String email) {
            return storageByEmail.get(email.toLowerCase());
        }

        @Override
        public User findByUniversityId(String universityId) {
            return storageByUnivId.get(universityId.toLowerCase());
        }

        @Override
        public User findById(Long id) {
            return storageById.get(id);
        }

        @Override
        public boolean save(User user) {
            long newId = ++idSequence;
            user.setId(newId);
            // Store defensive clone to preserve passwordHash before service clears it
            User clone = new User(
                    user.getUniversityId(),
                    user.getFullName(),
                    user.getEmail(),
                    user.getPhone(),
                    user.getPasswordHash(),
                    user.getDepartment(),
                    user.getSemester(),
                    user.getCampusName()
            );
            clone.setId(newId);
            storageById.put(newId, clone);
            storageByEmail.put(user.getEmail().toLowerCase(), clone);
            storageByUnivId.put(user.getUniversityId().toLowerCase(), clone);
            return true;
        }

        @Override
        public boolean updateProfile(User user) {
            User existing = storageById.get(user.getId());
            if (existing != null) {
                existing.setFullName(user.getFullName());
                existing.setPhone(user.getPhone());
                existing.setDepartment(user.getDepartment());
                existing.setSemester(user.getSemester());
                existing.setCampusName(user.getCampusName());
                return true;
            }
            return false;
        }

        @Override
        public boolean updatePassword(Long userId, String newPasswordHash) {
            User existing = storageById.get(userId);
            if (existing != null) {
                existing.setPasswordHash(newPasswordHash);
                return true;
            }
            return false;
        }
    }
}
