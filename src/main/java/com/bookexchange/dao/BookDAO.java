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
 * 1. Safe PreparedStatement parameterized queries (preventing SQL injection).
 * 2. Dynamic multi-criteria search and filter SQL construction.
 * 3. Enforced backend-level ownership validation (WHERE id = ? AND owner_id = ?).
 * 4. Referential integrity checks before deletion (preventing deletion of books in active exchanges).
 * 5. Java 7+ try-with-resources lifecycle management.
 * ============================================================================
 */
public class BookDAO {

    /**
     * Retrieves all available academic books.
     * Optionally excludes the currently logged-in student's own books.
     *
     * @param excludeOwnerId Student ID to exclude (or null to include all)
     * @return List of available books
     * @throws SQLException On database error
     */
    public List<Book> findAllAvailable(Long excludeOwnerId) throws SQLException {
        List<Book> bookList = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT b.id, b.owner_id, s.full_name AS owner_name, s.department AS owner_department, s.campus_name AS owner_campus, " +
                "b.title, b.author, b.isbn, b.edition, b.course_code, b.subject, b.condition_grade, b.sharing_mode, b.status, " +
                "b.description, b.image_url, b.lending_max_duration_days, b.swap_preferences, b.created_at, b.updated_at " +
                "FROM books b " +
                "JOIN students s ON b.owner_id = s.id " +
                "WHERE b.status = 'AVAILABLE' "
        );

        if (excludeOwnerId != null) {
            sql.append("AND b.owner_id <> ? ");
        }
        sql.append("ORDER BY b.created_at DESC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            if (excludeOwnerId != null) {
                stmt.setLong(1, excludeOwnerId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    bookList.add(mapRowToBook(rs));
                }
            }
        }
        return bookList;
    }

    /**
     * Backward-compatible overload without exclusion.
     */
    public List<Book> findAllAvailable() throws SQLException {
        return findAllAvailable(null);
    }

    /**
     * Finds a single book by its primary key ID, joined with owner info.
     *
     * @param id The book primary key
     * @return Book model or null
     * @throws SQLException On database error
     */
    public Book findById(Long id) throws SQLException {
        String sql = "SELECT b.id, b.owner_id, s.full_name AS owner_name, s.department AS owner_department, s.campus_name AS owner_campus, " +
                     "b.title, b.author, b.isbn, b.edition, b.course_code, b.subject, b.condition_grade, b.sharing_mode, b.status, " +
                     "b.description, b.image_url, b.lending_max_duration_days, b.swap_preferences, b.created_at, b.updated_at " +
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
     * Retrieves all books uploaded by a specific student (for My Books).
     *
     * @param ownerId The student's ID
     * @param statusFilter Optional status filter (or null/"ALL" for all statuses)
     * @return List of student's own books
     * @throws SQLException On database error
     */
    public List<Book> findByOwnerId(Long ownerId, String statusFilter) throws SQLException {
        List<Book> bookList = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT b.id, b.owner_id, s.full_name AS owner_name, s.department AS owner_department, s.campus_name AS owner_campus, " +
                "b.title, b.author, b.isbn, b.edition, b.course_code, b.subject, b.condition_grade, b.sharing_mode, b.status, " +
                "b.description, b.image_url, b.lending_max_duration_days, b.swap_preferences, b.created_at, b.updated_at " +
                "FROM books b " +
                "JOIN students s ON b.owner_id = s.id " +
                "WHERE b.owner_id = ? "
        );

        boolean hasStatus = statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter);
        if (hasStatus) {
            sql.append("AND b.status = ? ");
        }
        sql.append("ORDER BY b.created_at DESC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            stmt.setLong(1, ownerId);
            if (hasStatus) {
                stmt.setString(2, statusFilter.trim().toUpperCase());
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    bookList.add(mapRowToBook(rs));
                }
            }
        }
        return bookList;
    }

    /**
     * Performs dynamic, parameterized search and multi-criteria filtering on available books.
     * Searches across Title, Author, Subject, and ISBN (case-insensitive).
     *
     * @param query Search keywords
     * @param subject Academic subject filter
     * @param condition Condition grade filter
     * @param mode Sharing mode filter
     * @param excludeOwnerId Current logged-in student to exclude from explore results
     * @return Filtered list of books
     * @throws SQLException On database error
     */
    public List<Book> searchAndFilter(String query, String subject, String condition, String mode, Long excludeOwnerId)
            throws SQLException {

        List<Book> bookList = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT b.id, b.owner_id, s.full_name AS owner_name, s.department AS owner_department, s.campus_name AS owner_campus, " +
                "b.title, b.author, b.isbn, b.edition, b.course_code, b.subject, b.condition_grade, b.sharing_mode, b.status, " +
                "b.description, b.image_url, b.lending_max_duration_days, b.swap_preferences, b.created_at, b.updated_at " +
                "FROM books b " +
                "JOIN students s ON b.owner_id = s.id " +
                "WHERE b.status = 'AVAILABLE' "
        );

        List<Object> params = new ArrayList<>();

        if (excludeOwnerId != null) {
            sql.append("AND b.owner_id <> ? ");
            params.add(excludeOwnerId);
        }

        if (query != null && !query.trim().isEmpty()) {
            String wildcard = "%" + query.trim().toLowerCase() + "%";
            sql.append("AND (LOWER(b.title) LIKE ? OR LOWER(b.author) LIKE ? OR LOWER(b.subject) LIKE ? OR LOWER(b.isbn) LIKE ?) ");
            params.add(wildcard);
            params.add(wildcard);
            params.add(wildcard);
            params.add(wildcard);
        }

        if (subject != null && !subject.trim().isEmpty() && !"ALL".equalsIgnoreCase(subject)) {
            sql.append("AND b.subject = ? ");
            params.add(subject.trim());
        }

        if (condition != null && !condition.trim().isEmpty() && !"ALL".equalsIgnoreCase(condition)) {
            sql.append("AND b.condition_grade = ? ");
            params.add(condition.trim().toUpperCase());
        }

        if (mode != null && !mode.trim().isEmpty() && !"ALL".equalsIgnoreCase(mode)) {
            sql.append("AND b.sharing_mode = ? ");
            params.add(mode.trim().toUpperCase());
        }

        sql.append("ORDER BY b.created_at DESC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Long) {
                    stmt.setLong(i + 1, (Long) p);
                } else {
                    stmt.setString(i + 1, (String) p);
                }
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    bookList.add(mapRowToBook(rs));
                }
            }
        }
        return bookList;
    }

    /**
     * Backward-compatible search by subject.
     */
    public List<Book> findBySubject(String subject) throws SQLException {
        return searchAndFilter(null, subject, null, null, null);
    }

    /**
     * Backward-compatible search by sharing mode.
     */
    public List<Book> findBySharingMode(String mode) throws SQLException {
        return searchAndFilter(null, null, null, mode, null);
    }

    /**
     * Persists a new academic book listing.
     *
     * @param book The book object to insert
     * @return true on success
     * @throws SQLException On database errors
     */
    public boolean save(Book book) throws SQLException {
        String sql = "INSERT INTO books (owner_id, title, author, isbn, edition, course_code, subject, " +
                     "condition_grade, sharing_mode, status, description, image_url, lending_max_duration_days, swap_preferences) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
            stmt.setString(12, book.getImageUrl());

            if (book.getLendingMaxDurationDays() != null) {
                stmt.setInt(13, book.getLendingMaxDurationDays());
            } else {
                stmt.setNull(13, Types.INTEGER);
            }

            stmt.setString(14, book.getSwapPreferences());

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
     * Updates an existing book listing.
     * Enforces ownership in the SQL WHERE clause: WHERE id = ? AND owner_id = ?.
     *
     * @param book The updated book details
     * @param ownerId The authenticated student's ID
     * @return true if updated, false if book not found or student is not owner
     * @throws SQLException On database error
     */
    public boolean update(Book book, Long ownerId) throws SQLException {
        String sql = "UPDATE books SET title = ?, author = ?, isbn = ?, edition = ?, course_code = ?, " +
                     "subject = ?, condition_grade = ?, sharing_mode = ?, status = ?, description = ?, " +
                     "image_url = ?, lending_max_duration_days = ?, swap_preferences = ? " +
                     "WHERE id = ? AND owner_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, book.getTitle());
            stmt.setString(2, book.getAuthor());
            stmt.setString(3, book.getIsbn());
            stmt.setString(4, book.getEdition());
            stmt.setString(5, book.getCourseCode());
            stmt.setString(6, book.getSubject());
            stmt.setString(7, book.getConditionGrade());
            stmt.setString(8, book.getSharingMode());
            stmt.setString(9, book.getStatus());
            stmt.setString(10, book.getDescription());
            stmt.setString(11, book.getImageUrl());

            if (book.getLendingMaxDurationDays() != null) {
                stmt.setInt(12, book.getLendingMaxDurationDays());
            } else {
                stmt.setNull(12, Types.INTEGER);
            }

            stmt.setString(13, book.getSwapPreferences());
            stmt.setLong(14, book.getId());
            stmt.setLong(15, ownerId);

            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Deletes a book listing if it belongs to the authenticated student.
     *
     * @param bookId The book to delete
     * @param ownerId The authenticated student's ID
     * @return true if deleted, false otherwise
     * @throws SQLException On database error
     */
    public boolean delete(Long bookId, Long ownerId) throws SQLException {
        String sql = "DELETE FROM books WHERE id = ? AND owner_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, bookId);
            stmt.setLong(2, ownerId);

            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Checks if a book is currently involved in an active or accepted exchange.
     * Deletion must be blocked if active exchanges exist.
     *
     * @param bookId The book to check
     * @return true if active exchange exists, false otherwise
     * @throws SQLException On database error
     */
    public boolean isBookInActiveExchange(Long bookId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM exchanges WHERE book_id = ? AND status IN ('ACTIVE', 'COMPLETED')";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, bookId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Updates availability status.
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
     * Maps a ResultSet row to a Book object safely.
     */
    private Book mapRowToBook(ResultSet rs) throws SQLException {
        Book book = new Book();
        book.setId(rs.getLong("id"));
        book.setOwnerId(rs.getLong("owner_id"));
        book.setOwnerName(rs.getString("owner_name"));

        try {
            book.setOwnerDepartment(rs.getString("owner_department"));
            book.setOwnerCampus(rs.getString("owner_campus"));
        } catch (SQLException ignored) {
        }

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

        try {
            book.setImageUrl(rs.getString("image_url"));
        } catch (SQLException ignored) {
        }

        int lendingDays = rs.getInt("lending_max_duration_days");
        if (!rs.wasNull()) {
            book.setLendingMaxDurationDays(lendingDays);
        }

        book.setSwapPreferences(rs.getString("swap_preferences"));
        book.setCreatedAt(rs.getTimestamp("created_at"));

        try {
            book.setUpdatedAt(rs.getTimestamp("updated_at"));
        } catch (SQLException ignored) {
        }

        return book;
    }
}
