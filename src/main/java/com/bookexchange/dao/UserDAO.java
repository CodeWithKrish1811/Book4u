package com.bookexchange.dao;

import com.bookexchange.config.DBConnection;
import com.bookexchange.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * UserDAO (Data Access Object)
 * ============================================================================
 * Encapsulates all raw JDBC operations for the 'students' database table.
 * Demonstrates:
 * 1. PreparedStatement parameterized SQL execution (prevents SQL injection).
 * 2. Proper resource management using Java 7+ try-with-resources.
 * 3. Mapping relational ResultSet rows to Object-Oriented User instances.
 * ============================================================================
 */
public class UserDAO {

    /**
     * Finds a registered student by their unique email address.
     *
     * @param email The university email to search for
     * @return User object if found, or null if no record exists
     * @throws SQLException On database errors
     */
    public User findByEmail(String email) throws SQLException {
        String sql = "SELECT id, university_id, full_name, email, phone, password_hash, department, semester, campus_name, created_at " +
                     "FROM students WHERE email = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        }
        return null;
    }

    /**
     * Finds a student by their primary key ID.
     *
     * @param id Student primary key
     * @return User object or null
     * @throws SQLException On database errors
     */
    public User findById(Long id) throws SQLException {
        String sql = "SELECT id, university_id, full_name, email, phone, password_hash, department, semester, campus_name, created_at " +
                     "FROM students WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

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
     * Inserts a new student account into the database.
     *
     * @param user The student object to persist
     * @return true if insertion succeeded, false otherwise
     * @throws SQLException On database errors
     */
    public boolean save(User user) throws SQLException {
        String sql = "INSERT INTO students (university_id, full_name, email, phone, password_hash, department, semester, campus_name) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

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
        return user;
    }
}
