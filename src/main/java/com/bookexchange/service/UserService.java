package com.bookexchange.service;

import com.bookexchange.dao.UserDAO;
import com.bookexchange.model.User;
import com.bookexchange.util.PasswordUtil;
import com.bookexchange.util.ValidationUtil;

import java.sql.SQLException;

/**
 * ============================================================================
 * UserService - Business Logic Layer
 * ============================================================================
 * Enforces business rules for student authentication and profile management.
 * Coordinates between Servlets and DAOs.
 * ============================================================================
 */
public class UserService {

    private final UserDAO userDAO;

    public UserService() {
        this.userDAO = new UserDAO();
    }

    // Constructor injection for testability
    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /**
     * Registers a new student on the platform.
     * Enforces email format, password hashing, and duplicate prevention.
     *
     * @param user Student data
     * @param rawPassword Plaintext candidate password
     * @return Registered User object with populated ID
     * @throws IllegalArgumentException on validation failure
     * @throws SQLException on database error
     */
    public User registerStudent(User user, String rawPassword) throws SQLException {
        if (!ValidationUtil.isValidEmail(user.getEmail())) {
            throw new IllegalArgumentException("Invalid university email format: " + user.getEmail());
        }
        if (!ValidationUtil.isNotEmpty(rawPassword) || rawPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long.");
        }

        // Check if email already exists
        User existing = userDAO.findByEmail(user.getEmail());
        if (existing != null) {
            throw new IllegalArgumentException("An account with email " + user.getEmail() + " already exists.");
        }

        // Securely hash password
        String hashedPassword = PasswordUtil.hashPassword(rawPassword);
        user.setPasswordHash(hashedPassword);

        boolean success = userDAO.save(user);
        if (!success) {
            throw new SQLException("Failed to save student account into database.");
        }

        return user;
    }

    /**
     * Authenticates a student via email and password.
     *
     * @param email University email
     * @param rawPassword Plaintext password candidate
     * @return User object on successful authentication, or null
     * @throws SQLException on database error
     */
    public User authenticate(String email, String rawPassword) throws SQLException {
        if (!ValidationUtil.isValidEmail(email) || !ValidationUtil.isNotEmpty(rawPassword)) {
            return null;
        }

        User user = userDAO.findByEmail(email.trim());
        if (user == null) {
            return null; // User not found
        }

        // Verify cryptographic hash
        if (PasswordUtil.verifyPassword(rawPassword, user.getPasswordHash())) {
            return user;
        }

        return null; // Password mismatch
    }

    public User getStudentById(Long id) throws SQLException {
        return userDAO.findById(id);
    }
}
