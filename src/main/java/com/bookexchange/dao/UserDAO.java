package com.bookexchange.dao;

import com.bookexchange.config.DBConnection;
import com.bookexchange.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * UserDAO (Data Access Object) - High Performance JDBC Data Access
 * ============================================================================
 * Encapsulates all raw JDBC operations for the 'students' database table.
 * Demonstrates:
 * 1. PreparedStatement parameterized SQL execution (prevents SQL injection).
 * 2. Proper resource management using Java 7+ try-with-resources.
 * 3. Index-accelerated uniqueness and identity lookups with LIMIT 1.
 * 4. Fast existence checks with SELECT 1 ... LIMIT 1.
 * 5. Connection propagation support.
 * ============================================================================
 */
public class UserDAO {

    /**
     * Finds a registered student by their unique email address using an existing connection.
     * Evaluates exact indexed match first, falling back to case-insensitive comparison, terminated with LIMIT 1.
     */
    public User findByEmail(Connection conn, String email) throws SQLException {
        String trimmed = email.trim();
        String sql = "SELECT id, university_id, full_name, email, phone, password_hash, department, semester, campus_name, created_at " +
                     "FROM students WHERE email = ? OR LOWER(email) = LOWER(?) LIMIT 1";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, trimmed);
            stmt.setString(2, trimmed);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        }
        return null;
    }

    /**
     * Finds a registered student by their unique email address (standalone connection).
     */
    public User findByEmail(String email) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findByEmail(conn, email);
        }
    }

    /**
     * Finds a registered student by their unique university roll number / ID within an existing connection.
     */
    public User findByUniversityId(Connection conn, String universityId) throws SQLException {
        String trimmed = universityId.trim();
        String sql = "SELECT id, university_id, full_name, email, phone, password_hash, department, semester, campus_name, created_at " +
                     "FROM students WHERE university_id = ? OR LOWER(university_id) = LOWER(?) LIMIT 1";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, trimmed);
            stmt.setString(2, trimmed);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        }
        return null;
    }

    /**
     * Finds a registered student by their unique university roll number / ID (standalone connection).
     */
    public User findByUniversityId(String universityId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findByUniversityId(conn, universityId);
        }
    }

    /**
     * Finds a student by their primary key ID within an existing connection.
     */
    public User findById(Connection conn, Long id) throws SQLException {
        String sql = "SELECT id, university_id, full_name, email, phone, password_hash, department, semester, campus_name, created_at " +
                     "FROM students WHERE id = ? LIMIT 1";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        }
        return null;
    }

    /**
     * Finds a student by their primary key ID (standalone connection).
     */
    public User findById(Long id) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        }
    }

    /**
     * Inserts a new student account into the database within an active transaction.
     */
    public boolean save(Connection conn, User user) throws SQLException {
        String sql = "INSERT INTO students (university_id, full_name, email, phone, password_hash, department, semester, campus_name) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, user.getUniversityId());
            stmt.setString(2, user.getFullName());
            stmt.setString(3, user.getEmail());
            stmt.setString(4, user.getPhone());
            stmt.setString(5, user.getPasswordHash());
            stmt.setString(6, user.getDepartment());
            stmt.setInt(7, user.getSemester());
            stmt.setString(8, user.getCampusName());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        user.setId(generatedKeys.getLong(1));
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Inserts a new student account into the database (standalone connection).
     */
    public boolean save(User user) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return save(conn, user);
        }
    }

    /**
     * Updates permitted profile fields within an active transaction.
     */
    public boolean updateProfile(Connection conn, User user) throws SQLException {
        String sql = "UPDATE students SET full_name = ?, phone = ?, department = ?, semester = ?, campus_name = ? " +
                     "WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getFullName());
            stmt.setString(2, user.getPhone());
            stmt.setString(3, user.getDepartment());
            stmt.setInt(4, user.getSemester());
            stmt.setString(5, user.getCampusName());
            stmt.setLong(6, user.getId());

            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Updates permitted profile fields (standalone connection).
     */
    public boolean updateProfile(User user) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return updateProfile(conn, user);
        }
    }

    /**
     * Securely updates a student's password hash within an active transaction.
     */
    public boolean updatePassword(Connection conn, Long userId, String newPasswordHash) throws SQLException {
        String sql = "UPDATE students SET password_hash = ? WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newPasswordHash);
            stmt.setLong(2, userId);

            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Securely updates a student's password hash (standalone connection).
     */
    public boolean updatePassword(Long userId, String newPasswordHash) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return updatePassword(conn, userId, newPasswordHash);
        }
    }

    /**
     * Checks if an email address is already registered by any other student.
     * Uses SELECT 1 ... LIMIT 1 for instant response.
     */
    public boolean isEmailTakenByOther(Connection conn, String email, Long currentUserId) throws SQLException {
        String trimmed = email.trim();
        String sql = "SELECT 1 FROM students WHERE (email = ? OR LOWER(email) = LOWER(?)) AND id <> ? LIMIT 1";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, trimmed);
            stmt.setString(2, trimmed);
            stmt.setLong(3, currentUserId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Checks if an email address is already registered by any other student (standalone connection).
     */
    public boolean isEmailTakenByOther(String email, Long currentUserId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return isEmailTakenByOther(conn, email, currentUserId);
        }
    }

    /**
     * Checks if a university ID is already assigned to another student.
     * Uses SELECT 1 ... LIMIT 1 for instant response.
     */
    public boolean isUniversityIdTakenByOther(Connection conn, String universityId, Long currentUserId) throws SQLException {
        String trimmed = universityId.trim();
        String sql = "SELECT 1 FROM students WHERE (university_id = ? OR LOWER(university_id) = LOWER(?)) AND id <> ? LIMIT 1";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, trimmed);
            stmt.setString(2, trimmed);
            stmt.setLong(3, currentUserId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Checks if a university ID is already assigned to another student (standalone connection).
     */
    public boolean isUniversityIdTakenByOther(String universityId, Long currentUserId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return isUniversityIdTakenByOther(conn, universityId, currentUserId);
        }
    }

    /**
     * Helper method to map a single ResultSet row to a User model object.
     */
    private User mapRowToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUniversityId(rs.getString("university_id"));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setPhone(rs.getString("phone"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setDepartment(rs.getString("department"));
        user.setSemester(rs.getInt("semester"));
        user.setCampusName(rs.getString("campus_name"));
        user.setCreatedAt(rs.getTimestamp("created_at"));

        try {
            user.setUpdatedAt(rs.getTimestamp("updated_at"));
        } catch (SQLException ignored) {
        }

        return user;
    }
}
