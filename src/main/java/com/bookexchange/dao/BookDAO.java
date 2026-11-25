package com.bookexchange.dao;

import com.bookexchange.config.DBConnection;
import com.bookexchange.model.Book;
import com.bookexchange.util.BookCoverResolver;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * BookDAO (Data Access Object) - High Performance JDBC Data Access
 * ============================================================================
 * Encapsulates all raw JDBC operations for the 'books' database table.
 * Demonstrates:
 * 1. Safe PreparedStatement parameterized queries (preventing SQL injection).
 * 2. Optimized multi-criteria filtering using indexed columns (status, created_at, isbn).
 * 3. Fast pagination support via LIMIT and OFFSET.
 * 4. Lightweight COUNT(*) queries without redundant table joins.
 * 5. Connection propagation support for transactional execution.
 * 6. Short-circuit point lookups with LIMIT 1 and SELECT 1 existence probes.
 * ============================================================================
 */
public class BookDAO {

    /**
     * Retrieves available academic books with pagination support.
     * Optionally excludes the currently logged-in student's own books.
     *
     * @param excludeOwnerId Student ID to exclude (or null to include all)
     * @param offset Starting record index (0-based)
     * @param limit Maximum rows to return (0 or negative for all)
     * @return List of available books
     * @throws SQLException On database error
     */
    public List<Book> findAllAvailable(Long excludeOwnerId, int offset, int limit) throws SQLException {
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
        sql.append("ORDER BY b.created_at DESC, b.id DESC");

        if (limit > 0) {
            sql.append(" LIMIT ? OFFSET ?");
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (excludeOwnerId != null) {
                stmt.setLong(paramIndex++, excludeOwnerId);
            }
            if (limit > 0) {
                stmt.setInt(paramIndex++, limit);
                stmt.setInt(paramIndex++, Math.max(0, offset));
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
     * Backward-compatible overload for unpaginated listing with exclusion.
     */
    public List<Book> findAllAvailable(Long excludeOwnerId) throws SQLException {
        return findAllAvailable(excludeOwnerId, 0, 0);
    }

    /**
     * Backward-compatible overload without exclusion or pagination.
     */
    public List<Book> findAllAvailable() throws SQLException {
        return findAllAvailable(null, 0, 0);
    }

    /**
     * Counts all available books without redundant table joins for maximum query efficiency.
     */
    public int countAllAvailable(Long excludeOwnerId) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM books b WHERE b.status = 'AVAILABLE' ");
        if (excludeOwnerId != null) {
            sql.append("AND b.owner_id <> ? ");
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            if (excludeOwnerId != null) {
                stmt.setLong(1, excludeOwnerId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Finds a single book by its primary key ID within an existing connection.
     * Uses LIMIT 1 to signal early termination to the query planner.
     */
    public Book findById(Connection conn, Long id) throws SQLException {
        String sql = "SELECT b.id, b.owner_id, s.full_name AS owner_name, s.department AS owner_department, s.campus_name AS owner_campus, " +
                     "b.title, b.author, b.isbn, b.edition, b.course_code, b.subject, b.condition_grade, b.sharing_mode, b.status, " +
                     "b.description, b.image_url, b.lending_max_duration_days, b.swap_preferences, b.created_at, b.updated_at " +
                     "FROM books b " +
                     "JOIN students s ON b.owner_id = s.id " +
                     "WHERE b.id = ? LIMIT 1";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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
     * Finds a single book by its primary key ID (standalone connection).
     */
    public Book findById(Long id) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        }
    }

    /**
     * Retrieves books uploaded by a specific student with optional pagination.
     *
     * @param ownerId The student's ID
     * @param statusFilter Optional status filter (or null/"ALL" for all statuses)
     * @param offset Starting row offset
     * @param limit Max rows (0 or negative for all)
     * @return List of student's own books
     * @throws SQLException On database error
     */
    public List<Book> findByOwnerId(Long ownerId, String statusFilter, int offset, int limit) throws SQLException {
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
        sql.append("ORDER BY b.created_at DESC, b.id DESC");

        if (limit > 0) {
            sql.append(" LIMIT ? OFFSET ?");
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            stmt.setLong(paramIndex++, ownerId);
            if (hasStatus) {
                stmt.setString(paramIndex++, statusFilter.trim().toUpperCase());
            }
            if (limit > 0) {
                stmt.setInt(paramIndex++, limit);
                stmt.setInt(paramIndex++, Math.max(0, offset));
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
     * Backward-compatible unpaginated overload.
     */
    public List<Book> findByOwnerId(Long ownerId, String statusFilter) throws SQLException {
        return findByOwnerId(ownerId, statusFilter, 0, 0);
    }

    /**
     * Counts books owned by a student without joins.
     */
    public int countByOwnerId(Long ownerId, String statusFilter) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM books b WHERE b.owner_id = ? ");
        boolean hasStatus = statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter);
        if (hasStatus) {
            sql.append("AND b.status = ? ");
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            stmt.setLong(1, ownerId);
            if (hasStatus) {
                stmt.setString(2, statusFilter.trim().toUpperCase());
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Performs dynamic, parameterized search and multi-criteria filtering on available books with pagination.
     * Supports title, author, ISBN, course code, department, subject, condition, and sharing mode.
     *
     * @param query General search keyword
     * @param title Title filter
     * @param author Author filter
     * @param isbn ISBN filter
     * @param subject Academic subject filter
     * @param department Student department filter
     * @param courseCode Course code filter
     * @param condition Condition grade filter
     * @param mode Sharing mode filter
     * @param excludeOwnerId Current logged-in student to exclude from explore results
     * @param offset Pagination offset
     * @param limit Pagination page size (0 for unbounded)
     * @return Filtered list of books
     * @throws SQLException On database error
     */
    public List<Book> searchAndFilter(String query, String title, String author, String isbn,
                                      String subject, String department, String courseCode,
                                      String condition, String mode, Long excludeOwnerId,
                                      int offset, int limit) throws SQLException {

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
        appendSearchAndFilterCriteria(sql, params, query, title, author, isbn, subject, department, courseCode, condition, mode, excludeOwnerId);

        sql.append("ORDER BY b.created_at DESC, b.id DESC");
        if (limit > 0) {
            sql.append(" LIMIT ? OFFSET ?");
            params.add(limit);
            params.add(Math.max(0, offset));
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            bindParameters(stmt, params);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    bookList.add(mapRowToBook(rs));
                }
            }
        }
        return bookList;
    }

    /**
     * Backward-compatible search and filter with pagination.
     */
    public List<Book> searchAndFilter(String query, String subject, String condition, String mode, Long excludeOwnerId,
                                      int offset, int limit) throws SQLException {
        return searchAndFilter(query, null, null, null, subject, null, null, condition, mode, excludeOwnerId, offset, limit);
    }

    /**
     * Backward-compatible unpaginated search and filter overload.
     */
    public List<Book> searchAndFilter(String query, String subject, String condition, String mode, Long excludeOwnerId)
            throws SQLException {
        return searchAndFilter(query, subject, condition, mode, excludeOwnerId, 0, 0);
    }

    /**
     * Efficiently counts matching books for enhanced search and filter.
     */
    public int countSearchAndFilter(String query, String title, String author, String isbn,
                                    String subject, String department, String courseCode,
                                    String condition, String mode, Long excludeOwnerId) throws SQLException {

        StringBuilder sql = new StringBuilder();
        boolean needsStudentJoin = department != null && !department.trim().isEmpty() && !"ALL".equalsIgnoreCase(department);
        if (needsStudentJoin) {
            sql.append("SELECT COUNT(*) FROM books b JOIN students s ON b.owner_id = s.id WHERE b.status = 'AVAILABLE' ");
        } else {
            sql.append("SELECT COUNT(*) FROM books b WHERE b.status = 'AVAILABLE' ");
        }

        List<Object> params = new ArrayList<>();
        appendSearchAndFilterCriteria(sql, params, query, title, author, isbn, subject, department, courseCode, condition, mode, excludeOwnerId);

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            bindParameters(stmt, params);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Backward-compatible count search and filter overload.
     */
    public int countSearchAndFilter(String query, String subject, String condition, String mode, Long excludeOwnerId)
            throws SQLException {
        return countSearchAndFilter(query, null, null, null, subject, null, null, condition, mode, excludeOwnerId);
    }

    private void appendSearchAndFilterCriteria(StringBuilder sql, List<Object> params,
                                              String query, String title, String author, String isbn,
                                              String subject, String department, String courseCode,
                                              String condition, String mode, Long excludeOwnerId) {
        if (excludeOwnerId != null) {
            sql.append("AND b.owner_id <> ? ");
            params.add(excludeOwnerId);
        }

        // 1. General search query (across Title, Author, ISBN, Subject, Course Code)
        if (query != null && !query.trim().isEmpty()) {
            String trimmed = query.trim();
            String wildcard = "%" + trimmed.toLowerCase() + "%";
            String cleanIsbn = trimmed.replace("-", "").replace(" ", "");

            if (cleanIsbn.matches("^[0-9]{9}[0-9Xx]$") || cleanIsbn.matches("^[0-9]{13}$")) {
                // Index-accelerated path for ISBN searches
                sql.append("AND (b.isbn = ? OR b.isbn = ? OR LOWER(b.title) LIKE ? OR LOWER(b.author) LIKE ? OR LOWER(b.subject) LIKE ? OR LOWER(b.course_code) LIKE ?) ");
                params.add(trimmed);
                params.add(cleanIsbn);
                params.add(wildcard);
                params.add(wildcard);
                params.add(wildcard);
                params.add(wildcard);
            } else {
                sql.append("AND (LOWER(b.title) LIKE ? OR LOWER(b.author) LIKE ? OR LOWER(b.subject) LIKE ? OR LOWER(b.isbn) LIKE ? OR LOWER(b.course_code) LIKE ?) ");
                params.add(wildcard);
                params.add(wildcard);
                params.add(wildcard);
                params.add(wildcard);
                params.add(wildcard);
            }
        }

        // 2. Specific Title Search (case-insensitive)
        if (title != null && !title.trim().isEmpty()) {
            sql.append("AND LOWER(b.title) LIKE ? ");
            params.add("%" + title.trim().toLowerCase() + "%");
        }

        // 3. Specific Author Search (case-insensitive)
        if (author != null && !author.trim().isEmpty()) {
            sql.append("AND LOWER(b.author) LIKE ? ");
            params.add("%" + author.trim().toLowerCase() + "%");
        }

        // 4. Specific ISBN Search (index-accelerated + wildcard fallback)
        if (isbn != null && !isbn.trim().isEmpty()) {
            String rawIsbn = isbn.trim();
            String cleanIsbn = rawIsbn.replace("-", "").replace(" ", "");
            if (cleanIsbn.matches("^[0-9]{9}[0-9Xx]$") || cleanIsbn.matches("^[0-9]{13}$")) {
                sql.append("AND (b.isbn = ? OR b.isbn = ? OR LOWER(b.isbn) LIKE ?) ");
                params.add(rawIsbn);
                params.add(cleanIsbn);
                params.add("%" + cleanIsbn.toLowerCase() + "%");
            } else {
                sql.append("AND LOWER(b.isbn) LIKE ? ");
                params.add("%" + rawIsbn.toLowerCase() + "%");
            }
        }

        // 5. Academic Subject Filter (case-insensitive, handles "ALL")
        if (subject != null && !subject.trim().isEmpty() && !"ALL".equalsIgnoreCase(subject)) {
            sql.append("AND (b.subject = ? OR LOWER(b.subject) = LOWER(?)) ");
            params.add(subject.trim());
            params.add(subject.trim());
        }

        // 6. Academic Department Filter (from students s, case-insensitive, handles "ALL")
        if (department != null && !department.trim().isEmpty() && !"ALL".equalsIgnoreCase(department)) {
            String deptTrimmed = department.trim();
            sql.append("AND (s.department = ? OR LOWER(s.department) = LOWER(?) OR LOWER(s.department) LIKE ?) ");
            params.add(deptTrimmed);
            params.add(deptTrimmed);
            params.add("%" + deptTrimmed.toLowerCase() + "%");
        }

        // 7. Course Code Filter (b.course_code, case-insensitive, handles "ALL")
        if (courseCode != null && !courseCode.trim().isEmpty() && !"ALL".equalsIgnoreCase(courseCode)) {
            String courseTrimmed = courseCode.trim();
            sql.append("AND (b.course_code = ? OR LOWER(b.course_code) = LOWER(?) OR LOWER(b.course_code) LIKE ?) ");
            params.add(courseTrimmed);
            params.add(courseTrimmed);
            params.add("%" + courseTrimmed.toLowerCase() + "%");
        }

        // 8. Condition Grade Filter
        if (condition != null && !condition.trim().isEmpty() && !"ALL".equalsIgnoreCase(condition)) {
            sql.append("AND b.condition_grade = ? ");
            params.add(condition.trim().toUpperCase());
        }

        // 9. Sharing Mode Filter
        if (mode != null && !mode.trim().isEmpty() && !"ALL".equalsIgnoreCase(mode)) {
            sql.append("AND b.sharing_mode = ? ");
            params.add(mode.trim().toUpperCase());
        }
    }

    private void bindParameters(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if (p instanceof Long) {
                stmt.setLong(i + 1, (Long) p);
            } else if (p instanceof Integer) {
                stmt.setInt(i + 1, (Integer) p);
            } else {
                stmt.setString(i + 1, (String) p);
            }
        }
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
     * Persists a new academic book listing within an active connection transaction.
     */
    public boolean save(Connection conn, Book book) throws SQLException {
        String sql = "INSERT INTO books (owner_id, title, author, isbn, edition, course_code, subject, " +
                     "condition_grade, sharing_mode, status, description, image_url, lending_max_duration_days, swap_preferences) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
     * Persists a new academic book listing (standalone connection).
     */
    public boolean save(Book book) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return save(conn, book);
        }
    }

    /**
     * Updates an existing book listing within a connection transaction.
     */
    public boolean update(Connection conn, Book book, Long ownerId) throws SQLException {
        String sql = "UPDATE books SET title = ?, author = ?, isbn = ?, edition = ?, course_code = ?, " +
                     "subject = ?, condition_grade = ?, sharing_mode = ?, status = ?, description = ?, " +
                     "image_url = ?, lending_max_duration_days = ?, swap_preferences = ? " +
                     "WHERE id = ? AND owner_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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
     * Updates an existing book listing (standalone connection).
     */
    public boolean update(Book book, Long ownerId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, book, ownerId);
        }
    }

    /**
     * Deletes a book listing within an active transaction.
     */
    public boolean delete(Connection conn, Long bookId, Long ownerId) throws SQLException {
        String sql = "DELETE FROM books WHERE id = ? AND owner_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, bookId);
            stmt.setLong(2, ownerId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Deletes a book listing (standalone connection).
     */
    public boolean delete(Long bookId, Long ownerId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return delete(conn, bookId, ownerId);
        }
    }

    /**
     * Checks if a book is involved in an active exchange using a fast SELECT 1 existence query.
     */
    public boolean isBookInActiveExchange(Connection conn, Long bookId) throws SQLException {
        String sql = "SELECT 1 FROM exchanges WHERE book_id = ? AND status IN ('ACTIVE', 'COMPLETED') LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, bookId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Checks if a book is involved in an active exchange (standalone connection).
     */
    public boolean isBookInActiveExchange(Long bookId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return isBookInActiveExchange(conn, bookId);
        }
    }

    /**
     * Updates availability status with external connection (for transactions).
     */
    public boolean updateStatus(Connection conn, Long id, String status) throws SQLException {
        String sql = "UPDATE books SET status = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setLong(2, id);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Updates availability status (standalone connection).
     */
    public boolean updateStatus(Long id, String status) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return updateStatus(conn, id, status);
        }
    }

    /**
     * Atomically transfers ownership of a book to a new student and sets status to AVAILABLE.
     */
    public boolean transferOwnership(Connection conn, Long bookId, Long newOwnerId) throws SQLException {
        String sql = "UPDATE books SET owner_id = ?, status = 'AVAILABLE' WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, newOwnerId);
            stmt.setLong(2, bookId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Finds a book by primary key and acquires an exclusive row-level lock (FOR UPDATE)
     * within an active transaction.
     */
    public Book findByIdForUpdate(Connection conn, Long id) throws SQLException {
        String sql = "SELECT b.id, b.owner_id, s.full_name AS owner_name, s.department AS owner_department, s.campus_name AS owner_campus, " +
                     "b.title, b.author, b.isbn, b.edition, b.course_code, b.subject, b.condition_grade, b.sharing_mode, b.status, " +
                     "b.description, b.image_url, b.lending_max_duration_days, b.swap_preferences, b.created_at, b.updated_at " +
                     "FROM books b " +
                     "JOIN students s ON b.owner_id = s.id " +
                     "WHERE b.id = ? FOR UPDATE";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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
     * Finds all available books owned by a student (useful for populating SWAP offerings).
     */
    public List<Book> findAvailableByOwner(Long ownerId) throws SQLException {
        List<Book> list = new ArrayList<>();
        String sql = "SELECT b.id, b.owner_id, s.full_name AS owner_name, s.department AS owner_department, s.campus_name AS owner_campus, " +
                     "b.title, b.author, b.isbn, b.edition, b.course_code, b.subject, b.condition_grade, b.sharing_mode, b.status, " +
                     "b.description, b.image_url, b.lending_max_duration_days, b.swap_preferences, b.created_at, b.updated_at " +
                     "FROM books b " +
                     "JOIN students s ON b.owner_id = s.id " +
                     "WHERE b.owner_id = ? AND b.status = 'AVAILABLE' ORDER BY b.title ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, ownerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToBook(rs));
                }
            }
        }
        return list;
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
            String rawImg = rs.getString("image_url");
            String resolvedCover = BookCoverResolver.resolveCoverUrl(book.getTitle(), book.getAuthor(), book.getIsbn(), rawImg);
            book.setImageUrl(resolvedCover);
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
