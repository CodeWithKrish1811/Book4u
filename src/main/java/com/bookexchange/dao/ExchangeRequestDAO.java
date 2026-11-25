package com.bookexchange.dao;

import com.bookexchange.config.DBConnection;
import com.bookexchange.model.ExchangeRequest;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * ExchangeRequestDAO (Data Access Object) - High Performance JDBC Data Access
 * ============================================================================
 * Manages raw JDBC persistence for the 'exchange_requests' database table.
 * Adheres strictly to:
 * - PreparedStatement parameterized queries
 * - Try-with-resources resource management
 * - Connection propagation across transactions
 * - Concurrency protection with SELECT FOR UPDATE
 * - Short-circuit single row fetching via LIMIT 1
 * - Constant-time duplicate checking via SELECT 1 ... LIMIT 1
 * ============================================================================
 */
public class ExchangeRequestDAO {

    private static final String SELECT_JOIN_FIELDS =
            "er.id, er.book_id, er.requester_id, er.owner_id, er.exchange_mode, er.offered_book_id, " +
            "er.proposed_duration_days, er.request_message, er.status, er.handover_location, er.handover_time, " +
            "er.created_at, er.updated_at, " +
            "b.title AS book_title, b.author AS book_author, b.image_url AS book_image_url, b.subject AS book_subject, b.condition_grade AS book_condition, " +
            "req.full_name AS requester_name, req.email AS requester_email, req.department AS requester_dept, req.campus_name AS requester_campus, " +
            "own.full_name AS owner_name, own.email AS owner_email, own.department AS owner_dept, own.campus_name AS owner_campus, " +
            "ob.title AS offered_book_title, ob.author AS offered_book_author, ob.image_url AS offered_book_image_url, " +
            "ex.id AS exchange_id ";

    private static final String FROM_JOIN_CLAUSE =
            "FROM exchange_requests er " +
            "JOIN books b ON er.book_id = b.id " +
            "JOIN students req ON er.requester_id = req.id " +
            "JOIN students own ON er.owner_id = own.id " +
            "LEFT JOIN books ob ON er.offered_book_id = ob.id " +
            "LEFT JOIN exchanges ex ON ex.request_id = er.id ";

    /**
     * Persists a new exchange request and assigns the generated primary key.
     */
    public Long createRequest(ExchangeRequest request) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return createRequest(conn, request);
        }
    }

    /**
     * Persists a new exchange request inside a provided connection transaction.
     */
    public Long createRequest(Connection conn, ExchangeRequest request) throws SQLException {
        String sql = "INSERT INTO exchange_requests (book_id, requester_id, owner_id, exchange_mode, " +
                     "offered_book_id, proposed_duration_days, request_message, status, handover_location, handover_time) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, request.getBookId());
            stmt.setLong(2, request.getRequesterId());
            stmt.setLong(3, request.getOwnerId());
            stmt.setString(4, request.getExchangeMode());

            if (request.getOfferedBookId() != null) {
                stmt.setLong(5, request.getOfferedBookId());
            } else {
                stmt.setNull(5, Types.BIGINT);
            }

            if (request.getProposedDurationDays() != null) {
                stmt.setInt(6, request.getProposedDurationDays());
            } else {
                stmt.setNull(6, Types.INTEGER);
            }

            stmt.setString(7, request.getRequestMessage());
            stmt.setString(8, request.getStatus() != null ? request.getStatus() : "PENDING");
            stmt.setString(9, request.getHandoverLocation());

            if (request.getHandoverTime() != null) {
                stmt.setTimestamp(10, request.getHandoverTime());
            } else {
                stmt.setNull(10, Types.TIMESTAMP);
            }

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Creating exchange request failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long id = generatedKeys.getLong(1);
                    request.setId(id);
                    return id;
                } else {
                    throw new SQLException("Creating exchange request failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Retrieves an exchange request by ID within an existing connection.
     */
    public ExchangeRequest findById(Connection conn, Long id) throws SQLException {
        String sql = "SELECT " + SELECT_JOIN_FIELDS + FROM_JOIN_CLAUSE + "WHERE er.id = ? LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToExchangeRequest(rs);
                }
            }
        }
        return null;
    }

    /**
     * Retrieves a single exchange request with all joined metadata by primary key ID (standalone connection).
     */
    public ExchangeRequest findById(Long id) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        }
    }

    /**
     * Retrieves an exchange request by ID and acquires an exclusive row-level lock (FOR UPDATE).
     */
    public ExchangeRequest findByIdForUpdate(Connection conn, Long id) throws SQLException {
        String sql = "SELECT " + SELECT_JOIN_FIELDS + FROM_JOIN_CLAUSE + "WHERE er.id = ? FOR UPDATE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToExchangeRequest(rs);
                }
            }
        }
        return null;
    }

    /**
     * Retrieves requests sent by the specified student with connection propagation and pagination.
     */
    public List<ExchangeRequest> findByRequester(Connection conn, Long requesterId, int offset, int limit) throws SQLException {
        List<ExchangeRequest> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT " + SELECT_JOIN_FIELDS + FROM_JOIN_CLAUSE +
                "WHERE er.requester_id = ? ORDER BY er.created_at DESC, er.id DESC"
        );

        if (limit > 0) {
            sql.append(" LIMIT ? OFFSET ?");
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            stmt.setLong(1, requesterId);
            if (limit > 0) {
                stmt.setInt(2, limit);
                stmt.setInt(3, Math.max(0, offset));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToExchangeRequest(rs));
                }
            }
        }
        return list;
    }

    /**
     * Retrieves all requests sent by the specified student (standalone connection).
     */
    public List<ExchangeRequest> findByRequester(Long requesterId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findByRequester(conn, requesterId, 0, 0);
        }
    }

    /**
     * Retrieves requests received by the specified book owner with connection propagation and pagination.
     */
    public List<ExchangeRequest> findByOwner(Connection conn, Long ownerId, int offset, int limit) throws SQLException {
        List<ExchangeRequest> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT " + SELECT_JOIN_FIELDS + FROM_JOIN_CLAUSE +
                "WHERE er.owner_id = ? ORDER BY er.created_at DESC, er.id DESC"
        );

        if (limit > 0) {
            sql.append(" LIMIT ? OFFSET ?");
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            stmt.setLong(1, ownerId);
            if (limit > 0) {
                stmt.setInt(2, limit);
                stmt.setInt(3, Math.max(0, offset));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToExchangeRequest(rs));
                }
            }
        }
        return list;
    }

    /**
     * Retrieves all requests received by the specified book owner (standalone connection).
     */
    public List<ExchangeRequest> findByOwner(Long ownerId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findByOwner(conn, ownerId, 0, 0);
        }
    }

    /**
     * Checks if a requester already has a PENDING request for a given book within a transaction.
     * Uses SELECT 1 ... LIMIT 1 for instant short-circuit evaluation.
     */
    public int countPendingRequests(Connection conn, Long bookId, Long requesterId) throws SQLException {
        String sql = "SELECT 1 FROM exchange_requests WHERE book_id = ? AND requester_id = ? AND status = 'PENDING' LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, bookId);
            stmt.setLong(2, requesterId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? 1 : 0;
            }
        }
    }

    /**
     * Checks if a requester already has a PENDING request for a given book (standalone connection).
     */
    public int countPendingRequests(Long bookId, Long requesterId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return countPendingRequests(conn, bookId, requesterId);
        }
    }

    /**
     * Updates request status in an active transaction.
     */
    public boolean updateStatus(Connection conn, Long id, String status) throws SQLException {
        String sql = "UPDATE exchange_requests SET status = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setLong(2, id);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Updates request status (standalone connection).
     */
    public boolean updateStatus(Long id, String status) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return updateStatus(conn, id, status);
        }
    }

    /**
     * Automatically cancels/rejects other competing pending requests for a book once one request is accepted.
     */
    public int cancelOtherPendingRequestsForBook(Connection conn, Long bookId, Long acceptedRequestId) throws SQLException {
        String sql = "UPDATE exchange_requests SET status = 'REJECTED' WHERE book_id = ? AND id <> ? AND status = 'PENDING'";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, bookId);
            stmt.setLong(2, acceptedRequestId);
            return stmt.executeUpdate();
        }
    }

    /**
     * Maps a ResultSet row to an ExchangeRequest model.
     */
    private ExchangeRequest mapRowToExchangeRequest(ResultSet rs) throws SQLException {
        ExchangeRequest req = new ExchangeRequest();
        req.setId(rs.getLong("id"));
        req.setBookId(rs.getLong("book_id"));
        req.setRequesterId(rs.getLong("requester_id"));
        req.setOwnerId(rs.getLong("owner_id"));
        req.setExchangeMode(rs.getString("exchange_mode"));

        long offeredBookId = rs.getLong("offered_book_id");
        if (!rs.wasNull()) {
            req.setOfferedBookId(offeredBookId);
        }

        int duration = rs.getInt("proposed_duration_days");
        if (!rs.wasNull()) {
            req.setProposedDurationDays(duration);
        }

        req.setRequestMessage(rs.getString("request_message"));
        req.setStatus(rs.getString("status"));
        req.setHandoverLocation(rs.getString("handover_location"));
        req.setHandoverTime(rs.getTimestamp("handover_time"));
        req.setCreatedAt(rs.getTimestamp("created_at"));
        req.setUpdatedAt(rs.getTimestamp("updated_at"));

        // Book details
        req.setBookTitle(rs.getString("book_title"));
        req.setBookAuthor(rs.getString("book_author"));
        req.setBookImageUrl(rs.getString("book_image_url"));
        req.setBookSubject(rs.getString("book_subject"));
        req.setBookConditionGrade(rs.getString("book_condition"));

        // Requester details
        req.setRequesterName(rs.getString("requester_name"));
        req.setRequesterEmail(rs.getString("requester_email"));
        req.setRequesterDepartment(rs.getString("requester_dept"));
        req.setRequesterCampus(rs.getString("requester_campus"));

        // Owner details
        req.setOwnerName(rs.getString("owner_name"));
        req.setOwnerEmail(rs.getString("owner_email"));
        req.setOwnerDepartment(rs.getString("owner_dept"));
        req.setOwnerCampus(rs.getString("owner_campus"));

        // Offered book details
        req.setOfferedBookTitle(rs.getString("offered_book_title"));
        req.setOfferedBookAuthor(rs.getString("offered_book_author"));
        req.setOfferedBookImageUrl(rs.getString("offered_book_image_url"));

        // Exchange ID if accepted
        long exchangeId = rs.getLong("exchange_id");
        if (!rs.wasNull()) {
            req.setExchangeId(exchangeId);
        }

        return req;
    }
}
