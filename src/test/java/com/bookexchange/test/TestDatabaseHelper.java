package com.bookexchange.test;

import com.bookexchange.config.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * TestDatabaseHelper manages an isolated, in-memory MySQL-compatible H2 database
 * specifically for integration testing real transactions, concurrency, and DAO operations.
 * It strictly avoids running destructive operations on production databases.
 */
public class TestDatabaseHelper {

    public static final String H2_URL = "jdbc:h2:mem:book4u_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
    public static final String H2_USER = "sa";
    public static final String H2_PASSWORD = "";
    public static final String H2_DRIVER = "org.h2.Driver";

    public static void initDatabase() {
        DBConnection.setTestConfig(H2_URL, H2_USER, H2_PASSWORD, H2_DRIVER);
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP TABLE IF EXISTS exchanges CASCADE");
            stmt.execute("DROP TABLE IF EXISTS exchange_requests CASCADE");
            stmt.execute("DROP TABLE IF EXISTS books CASCADE");
            stmt.execute("DROP TABLE IF EXISTS students CASCADE");

            stmt.execute("""
                CREATE TABLE students (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    university_id VARCHAR(64) NOT NULL UNIQUE,
                    full_name VARCHAR(128) NOT NULL,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    phone VARCHAR(20) NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    department VARCHAR(128) NOT NULL,
                    semester INT NOT NULL DEFAULT 1,
                    campus_name VARCHAR(128) NOT NULL DEFAULT 'Main Campus',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("""
                CREATE TABLE books (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    owner_id BIGINT NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    author VARCHAR(255) NOT NULL,
                    isbn VARCHAR(32) NULL,
                    edition VARCHAR(32) NULL,
                    course_code VARCHAR(32) NULL,
                    subject VARCHAR(128) NOT NULL,
                    condition_grade VARCHAR(32) NOT NULL DEFAULT 'GOOD',
                    sharing_mode VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
                    description TEXT NULL,
                    image_url VARCHAR(512) NULL,
                    lending_max_duration_days INT NULL,
                    swap_preferences TEXT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_books_owner FOREIGN KEY (owner_id) REFERENCES students(id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE TABLE exchange_requests (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    book_id BIGINT NOT NULL,
                    requester_id BIGINT NOT NULL,
                    owner_id BIGINT NOT NULL,
                    exchange_mode VARCHAR(32) NOT NULL,
                    offered_book_id BIGINT NULL,
                    proposed_duration_days INT NULL,
                    request_message TEXT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    handover_location VARCHAR(255) NULL,
                    handover_time TIMESTAMP NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_requests_book FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
                    CONSTRAINT fk_requests_requester FOREIGN KEY (requester_id) REFERENCES students(id) ON DELETE CASCADE,
                    CONSTRAINT fk_requests_owner FOREIGN KEY (owner_id) REFERENCES students(id) ON DELETE CASCADE,
                    CONSTRAINT fk_requests_offered_book FOREIGN KEY (offered_book_id) REFERENCES books(id) ON DELETE SET NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE exchanges (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    request_id BIGINT NOT NULL UNIQUE,
                    book_id BIGINT NOT NULL,
                    offered_book_id BIGINT NULL,
                    owner_id BIGINT NOT NULL,
                    requester_id BIGINT NOT NULL,
                    exchange_mode VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'AWAITING_HANDOVER',
                    owner_handover_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
                    requester_handover_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
                    owner_return_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
                    requester_return_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
                    handover_location VARCHAR(255) NULL,
                    handover_notes TEXT NULL,
                    start_date DATE NULL,
                    due_date DATE NULL,
                    return_date DATE NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_exchanges_request FOREIGN KEY (request_id) REFERENCES exchange_requests(id) ON DELETE RESTRICT,
                    CONSTRAINT fk_exchanges_book FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE RESTRICT,
                    CONSTRAINT fk_exchanges_owner FOREIGN KEY (owner_id) REFERENCES students(id) ON DELETE RESTRICT,
                    CONSTRAINT fk_exchanges_requester FOREIGN KEY (requester_id) REFERENCES students(id) ON DELETE RESTRICT
                )
            """);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize test database", e);
        }
    }

    public static void clearTables() {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
            stmt.execute("TRUNCATE TABLE exchanges");
            stmt.execute("TRUNCATE TABLE exchange_requests");
            stmt.execute("TRUNCATE TABLE books");
            stmt.execute("TRUNCATE TABLE students");
            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear test tables", e);
        }
    }

    public static void insertStudent(Long id, String universityId, String fullName, String email, String dept, int semester, String campus) throws SQLException {
        String sql = "INSERT INTO students (id, university_id, full_name, email, password_hash, department, semester, campus_name) " +
                     "VALUES (?, ?, ?, ?, 'hash', ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.setString(2, universityId);
            stmt.setString(3, fullName);
            stmt.setString(4, email);
            stmt.setString(5, dept);
            stmt.setInt(6, semester);
            stmt.setString(7, campus);
            stmt.executeUpdate();
        }
    }

    public static void insertBook(Long id, Long ownerId, String title, String author, String mode, String status, Integer maxDuration) throws SQLException {
        String sql = "INSERT INTO books (id, owner_id, title, author, isbn, edition, course_code, subject, condition_grade, " +
                     "sharing_mode, status, description, lending_max_duration_days) " +
                     "VALUES (?, ?, ?, ?, '9780000000000', '1st', 'CS101', 'CS', 'LIKE_NEW', ?, ?, 'Test Desc', ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.setLong(2, ownerId);
            stmt.setString(3, title);
            stmt.setString(4, author);
            stmt.setString(5, mode);
            stmt.setString(6, status);
            if (maxDuration != null) {
                stmt.setInt(7, maxDuration);
            } else {
                stmt.setNull(7, java.sql.Types.INTEGER);
            }
            stmt.executeUpdate();
        }
    }
}
