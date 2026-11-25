package com.bookexchange.service;

import com.bookexchange.dao.UserDAO;
import com.bookexchange.model.User;
import com.bookexchange.util.PasswordUtil;
import com.bookexchange.util.ValidationUtil;

import java.sql.SQLException;

/**
 * ============================================================================
 * UserService - Business Logic Layer for Student Authentication & Profiles
 * ============================================================================
 * Coordinates between Servlets and UserDAO.
 * Enforces business rules:
 * 1. Strict validation of all registration inputs.
 * 2. Duplicate prevention for email and university ID.
 * 3. Password matching and minimum complexity verification.
 * 4. PBKDF2WithHmacSHA256 password hashing with unique salts.
 * 5. Secure profile modification and password change workflows.
 * ============================================================================
 */
public class UserService {

    private final UserDAO userDAO;

    public UserService() {
        this.userDAO = new UserDAO();
    }

    // Constructor injection for testing and mock DAOs
    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /**
     * Registers a new student on the Book4u campus platform.
     * Enforces non-empty inputs, email format, duplicate email/university ID checks,
     * password confirmation, and PBKDF2 hashing.
     *
     * @param user The student data object
     * @param rawPassword Plaintext password
     * @param confirmPassword Password confirmation string
     * @return Registered User with generated ID and cleared password hash
     * @throws IllegalArgumentException If any validation fails
     * @throws SQLException If database insertion fails
     */
    public User registerStudent(User user, String rawPassword, String confirmPassword) throws SQLException {
        if (user == null) {
            throw new IllegalArgumentException("Student information cannot be null.");
        }

        // 1. Mandatory field checks
        if (!ValidationUtil.isNotEmpty(user.getFullName())) {
            throw new IllegalArgumentException("Full Name is required.");
        }
        if (user.getFullName().trim().length() > 128) {
            throw new IllegalArgumentException("Full Name cannot exceed 128 characters.");
        }
        user.setFullName(user.getFullName().trim());

        if (!ValidationUtil.isNotEmpty(user.getUniversityId())) {
            throw new IllegalArgumentException("University ID / Roll Number is required.");
        }
        if (user.getUniversityId().trim().length() > 64) {
            throw new IllegalArgumentException("University ID cannot exceed 64 characters.");
        }
        user.setUniversityId(user.getUniversityId().trim());

        if (!ValidationUtil.isNotEmpty(user.getEmail())) {
            throw new IllegalArgumentException("College Email is required.");
        }
        if (user.getEmail().trim().length() > 255) {
            throw new IllegalArgumentException("College Email cannot exceed 255 characters.");
        }
        user.setEmail(user.getEmail().trim());

        if (!ValidationUtil.isNotEmpty(user.getDepartment())) {
            throw new IllegalArgumentException("Department is required.");
        }
        if (user.getDepartment().trim().length() > 128) {
            throw new IllegalArgumentException("Department cannot exceed 128 characters.");
        }
        user.setDepartment(user.getDepartment().trim());

        if (!ValidationUtil.isValidSemester(user.getSemester())) {
            throw new IllegalArgumentException("Semester must be between 1 and 12.");
        }

        // Phone validation (optional)
        if (ValidationUtil.isNotEmpty(user.getPhone())) {
            String trimmedPhone = user.getPhone().trim();
            if (trimmedPhone.length() > 20) {
                throw new IllegalArgumentException("Phone number cannot exceed 20 characters.");
            }
            if (!ValidationUtil.isValidPhone(trimmedPhone)) {
                throw new IllegalArgumentException("Invalid phone number format: " + trimmedPhone);
            }
            user.setPhone(trimmedPhone);
        } else {
            user.setPhone(null);
        }

        // Campus name validation (optional, defaults to Main Campus)
        if (ValidationUtil.isNotEmpty(user.getCampusName())) {
            String trimmedCampus = user.getCampusName().trim();
            if (trimmedCampus.length() > 128) {
                throw new IllegalArgumentException("Campus name cannot exceed 128 characters.");
            }
            user.setCampusName(trimmedCampus);
        } else {
            user.setCampusName("Main Campus");
        }

        // 2. Email format validation
        if (!ValidationUtil.isValidEmail(user.getEmail())) {
            throw new IllegalArgumentException("Invalid college email format: " + user.getEmail());
        }

        // 3. Password presence and length check
        if (!ValidationUtil.isNotEmpty(rawPassword)) {
            throw new IllegalArgumentException("Password is required.");
        }
        if (rawPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long.");
        }
        if (rawPassword.length() > 128) {
            throw new IllegalArgumentException("Password cannot exceed 128 characters.");
        }

        // 4. Password confirmation match
        if (confirmPassword == null || !rawPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Password and Confirm Password do not match.");
        }

        // 5. Duplicate email check
        User existingEmailUser = userDAO.findByEmail(user.getEmail());
        if (existingEmailUser != null) {
            throw new IllegalArgumentException("A student account with email '" + user.getEmail() + "' already exists.");
        }

        // 6. Duplicate university ID check
        User existingUnivUser = userDAO.findByUniversityId(user.getUniversityId());
        if (existingUnivUser != null) {
            throw new IllegalArgumentException("A student account with University ID '" + user.getUniversityId() + "' already exists.");
        }

        // 7. Secure password hashing with PBKDF2WithHmacSHA256 and unique salt
        String hashedPassword = PasswordUtil.hashPassword(rawPassword);
        user.setPasswordHash(hashedPassword);

        // Default campus name if empty
        if (!ValidationUtil.isNotEmpty(user.getCampusName())) {
            user.setCampusName("Main Campus");
        }

        // 8. Persist to database
        boolean saved = userDAO.save(user);
        if (!saved) {
            throw new SQLException("Failed to create student account in database.");
        }

        // Clear hash from memory before returning to caller
        user.setPasswordHash(null);
        return user;
    }

    /**
     * Backward-compatible overload for existing servlet calls.
     */
    public User registerStudent(User user, String rawPassword) throws SQLException {
        return registerStudent(user, rawPassword, rawPassword);
    }

    /**
     * Authenticates a student using their college email and password.
     *
     * @param email College email
     * @param rawPassword Plaintext password candidate
     * @return Authenticated User object (with password hash sanitized), or null if invalid
     * @throws SQLException On database errors
     */
    public User authenticate(String email, String rawPassword) throws SQLException {
        if (!ValidationUtil.isNotEmpty(email) || !ValidationUtil.isNotEmpty(rawPassword)) {
            return null;
        }

        User user = userDAO.findByEmail(email.trim());
        if (user == null) {
            return null; // Email not found
        }

        // Verify hash using constant-time cryptographic comparison
        if (PasswordUtil.verifyPassword(rawPassword, user.getPasswordHash())) {
            // Detach sensitive hash from session user
            user.setPasswordHash(null);
            return user;
        }

        return null; // Password mismatch
    }

    /**
     * Retrieves student profile details by primary key ID.
     */
    public User getStudentById(Long id) throws SQLException {
        if (id == null) {
            return null;
        }
        User user = userDAO.findById(id);
        if (user != null) {
            user.setPasswordHash(null);
        }
        return user;
    }

    /**
     * Updates permitted profile information for a student.
     * Note: University ID and Email are intentionally immutable here to preserve identity integrity.
     */
    public User updateProfile(Long userId, String fullName, String phone, String department, int semester, String campusName) throws SQLException {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User ID is required.");
        }
        if (!ValidationUtil.isNotEmpty(fullName)) {
            throw new IllegalArgumentException("Full Name cannot be blank.");
        }
        if (fullName.trim().length() > 128) {
            throw new IllegalArgumentException("Full Name cannot exceed 128 characters.");
        }
        if (!ValidationUtil.isNotEmpty(department)) {
            throw new IllegalArgumentException("Department cannot be blank.");
        }
        if (department.trim().length() > 128) {
            throw new IllegalArgumentException("Department cannot exceed 128 characters.");
        }
        if (!ValidationUtil.isValidSemester(semester)) {
            throw new IllegalArgumentException("Semester must be between 1 and 12.");
        }
        if (ValidationUtil.isNotEmpty(phone)) {
            String trimmedPhone = phone.trim();
            if (trimmedPhone.length() > 20) {
                throw new IllegalArgumentException("Phone number cannot exceed 20 characters.");
            }
            if (!ValidationUtil.isValidPhone(trimmedPhone)) {
                throw new IllegalArgumentException("Invalid phone number format: " + trimmedPhone);
            }
        }
        if (ValidationUtil.isNotEmpty(campusName) && campusName.trim().length() > 128) {
            throw new IllegalArgumentException("Campus name cannot exceed 128 characters.");
        }

        User existing = userDAO.findById(userId);
        if (existing == null) {
            throw new IllegalArgumentException("Student not found with ID: " + userId);
        }

        existing.setFullName(fullName.trim());
        existing.setPhone(ValidationUtil.isNotEmpty(phone) ? phone.trim() : null);
        existing.setDepartment(department.trim());
        existing.setSemester(semester);
        if (ValidationUtil.isNotEmpty(campusName)) {
            existing.setCampusName(campusName.trim());
        }

        boolean updated = userDAO.updateProfile(existing);
        if (!updated) {
            throw new SQLException("Failed to update student profile in database.");
        }

        existing.setPasswordHash(null);
        return existing;
    }

    /**
     * Securely changes a student's password after verifying their current password.
     */
    public void changePassword(Long userId, String currentPassword, String newPassword, String confirmPassword) throws SQLException {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User ID is required.");
        }
        if (!ValidationUtil.isNotEmpty(currentPassword)) {
            throw new IllegalArgumentException("Current password is required.");
        }
        if (currentPassword.length() > 128) {
            throw new IllegalArgumentException("Current password cannot exceed 128 characters.");
        }
        if (!ValidationUtil.isNotEmpty(newPassword)) {
            throw new IllegalArgumentException("New password is required.");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters long.");
        }
        if (newPassword.length() > 128) {
            throw new IllegalArgumentException("New password cannot exceed 128 characters.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("New password and confirm password do not match.");
        }

        // Fetch current student with password hash
        User student = userDAO.findById(userId);
        if (student == null) {
            throw new IllegalArgumentException("Student record not found.");
        }

        // Verify current password
        if (!PasswordUtil.verifyPassword(currentPassword, student.getPasswordHash())) {
            throw new IllegalArgumentException("Current password entered is incorrect.");
        }

        // Hash new password with PBKDF2WithHmacSHA256 and fresh salt
        String newHash = PasswordUtil.hashPassword(newPassword);

        boolean updated = userDAO.updatePassword(userId, newHash);
        if (!updated) {
            throw new SQLException("Failed to update password in database.");
        }
    }
}
