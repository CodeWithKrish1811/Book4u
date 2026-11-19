package com.bookexchange.dao;

import com.bookexchange.config.DBConnection;
import com.bookexchange.model.Book;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * BookDAO (Data Access Object)
 * ============================================================================
 * Encapsulates all raw JDBC operations for the 'books' database table.
 * Demonstrates:
 * 1. SQL JOIN queries (joining books with students to display owner's name).
 * 2. Parameterized queries for safe filtering by subject, sharing mode, and status.
 * 3. PreparedStatement resource cleanup.
 * ============================================================================
 */
public class BookDAO {

    /**
     * Retrieves all available academic books currently listed for peer sharing.
     *
     * @return List of Book objects with status 'AVAILABLE'
     * @throws SQLException On database errors
     */
    public List<Book> findAllAvailable() throws SQLException {
        List<Book> bookList = new ArrayList<>();
        String sql = "SELECT b.id, b.owner_id, s.full_name AS owner_name, b.title, b.author, b.isbn, " +
                     "b.edition, b.course_code, b.subject, b.condition_grade, b.sharing_mode, b.status, " +
                     "b.description, b.lending_max_duration_days, b.swap_preferences, b.created_at " +
                     "FROM books b " +
                     "JOIN students s ON b.owner_id = s.id " +
                     "WHERE b.status = 'AVAILABLE' " +
                     "ORDER BY b.created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                bookList.add(mapRowToBook(rs));
            }
        }
        return bookList;
    }

    /**
     * Finds a book by its primary key ID.
     *
     * @param id The book ID
     * @return Book object or null
     * @throws SQLException On database errors
     */
    public Book findById(Long id) throws SQLException {
        String sql = "SELECT b.id, b.owner_id, s.full_name AS owner_name, b.title, b.author, b.isbn, " +
                     "b.edition, b.course_code, b.subject, b.condition_grade, b.sharing_mode, b.status, " +
                     "b.description, b.lending_max_duration_days, b.swap_preferences, b.created_at " +
                     "FROM books b " +
                     "JOIN students s ON b.owner_id = s.id " +
                     "WHERE b.id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToBook(rs);
                }
            }
        }
        return null;
    }

    /**
     * Filters available books by academic subject (e.g. 'Java', 'DBMS', 'DSA', 'Networks').
     *
     * @param subject The subject to filter by
     * @return Matching available books
     * @throws SQLException On database errors
     */
    public List<Book> findBySubject(String subject) throws SQLException {
        List<Book> bookList = new ArrayList<>();
        String sql = "SELECT b.id, b.owner_id, s.full_name AS owner_name, b.title, b.author, b.isbn, " +
                     "b.edition, b.course_code, b.subject, b.condition_grade, b.sharing_mode, b.status, " +
                     "b.description, b.lending_max_duration_days, b.swap_preferences, b.created_at " +
                     "FROM books b " +
                     "JOIN students s ON b.owner_id = s.id " +
                     "WHERE b.status = 'AVAILABLE' AND b.subject = ? " +
                     "ORDER BY b.created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, subject);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    bookList.add(mapRowToBook(rs));
                }
            }
        }
        return bookList;
    }

    /**
     * Filters available books by sharing mode ('SWAP', 'GIVEAWAY', 'LENDING').
     *
     * @param mode The sharing mode
     * @return Matching available books
     * @throws SQLException On database errors
     */
    public List<Book> findBySharingMode(String mode) throws SQLException {
        List<Book> bookList = new ArrayList<>();
        String sql = "SELECT b.id, b.owner_id, s.full_name AS owner_name, b.title, b.author, b.isbn, " +
                     "b.edition, b.course_code, b.subject, b.condition_grade, b.sharing_mode, b.status, " +
                     "b.description, b.lending_max_duration_days, b.swap_preferences, b.created_at " +
                     "FROM books b " +
                     "JOIN students s ON b.owner_id = s.id " +
                     "WHERE b.status = 'AVAILABLE' AND b.sharing_mode = ? " +
                     "ORDER BY b.created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, mode);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    bookList.add(mapRowToBook(rs));
                }
            }
        }
        return bookList;
    }

    /**
     * Persists a new academic book listing into the database.
     *
     * @param book The book object to insert
     * @return true on success, false otherwise
     * @throws SQLException On database errors
     */
    public boolean save(Book book) throws SQLException {
        String sql = "INSERT INTO books (owner_id, title, author, isbn, edition, course_code, subject, " +
                     "condition_grade, sharing_mode, status, description, lending_max_duration_days, swap_preferences) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, book.getOwnerId());
            stmt.setString(2, book.getTitle());
            stmt.setString(3, book.getAuthor());
            stmt.setString(4, book.getIsbn());
            stmt.setString(5, book.getEdition());
            stmt.setString(6, book.getCourseCode());
            stmt.setString(7, book.getSubject());
            stmt.setString(8, book.getConditionGrade());
            stmt.setString(9, book.getSharingMode());
            stmt.setString(10, book.getStatus() != null ? book.getStatus() : "AVAILABLE");
            stmt.setString(11, book.getDescription());

            if (book.getLendingMaxDurationDays() != null) {
                stmt.setInt(12, book.getLendingMaxDurationDays());
            } else {
                stmt.setNull(12, Types.INTEGER);
            }

            stmt.setString(13, book.getSwapPreferences());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        book.setId(generatedKeys.getLong(1));
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Updates the status of a book listing (e.g. 'AVAILABLE', 'RESERVED', 'ON_LOAN', 'UNAVAILABLE').
     *
     * @param id The book ID
     * @param status The new status
     * @return true if updated, false otherwise
     * @throws SQLException On database errors
     */
    public boolean updateStatus(Long id, String status) throws SQLException {
        String sql = "UPDATE books SET status = ? WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            stmt.setLong(2, id);

            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Maps a ResultSet row to a Book object.
     */
    private Book mapRowToBook(ResultSet rs) throws SQLException {
        Book book = new Book();
        book.setId(rs.getLong("id"));
        book.setOwnerId(rs.getLong("owner_id"));
        book.setOwnerName(rs.getString("owner_name"));
        book.setTitle(rs.getString("title"));
        book.setAuthor(rs.getString("author"));
        book.setIsbn(rs.getString("isbn"));
        book.setEdition(rs.getString("edition"));
        book.setCourseCode(rs.getString("course_code"));
        book.setSubject(rs.getString("subject"));
        book.setConditionGrade(rs.getString("condition_grade"));
        book.setSharingMode(rs.getString("sharing_mode"));
        book.setStatus(rs.getString("status"));
        book.setDescription(rs.getString("description"));

        int lendingDays = rs.getInt("lending_max_duration_days");
        if (!rs.wasNull()) {
            book.setLendingMaxDurationDays(lendingDays);
        }

        book.setSwapPreferences(rs.getString("swap_preferences"));
        book.setCreatedAt(rs.getTimestamp("created_at"));
        return book;
    }
}
