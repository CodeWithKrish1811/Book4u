package com.bookexchange.dao;

import com.bookexchange.config.DBConnection;
import com.bookexchange.model.Exchange;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * ExchangeDAO (Data Access Object)
 * ============================================================================
 * Manages raw JDBC persistence for the 'exchanges' database table.
 * Adheres strictly to:
 * - PreparedStatement parameterized queries
 * - Try-with-resources resource management
 * - Transaction support with Connection propagation
 * - Dual handover & return confirmation tracking
 * - Concurrency protection with SELECT FOR UPDATE
 * ============================================================================
 */
public class ExchangeDAO {

    private static final String SELECT_JOIN_FIELDS =
            "ex.id, ex.request_id, ex.book_id, ex.offered_book_id, ex.owner_id, ex.requester_id, " +
            "ex.exchange_mode, ex.status, ex.owner_handover_confirmed, ex.requester_handover_confirmed, " +
            "ex.owner_return_confirmed, ex.requester_return_confirmed, ex.handover_location, ex.handover_notes, " +
            "ex.start_date, ex.due_date, ex.return_date, ex.created_at, ex.updated_at, " +
            "b.title AS book_title, b.author AS book_author, b.image_url AS book_image_url, b.subject AS book_subject, " +
            "ob.title AS offered_book_title, ob.author AS offered_book_author, ob.image_url AS offered_book_image_url, " +
            "own.full_name AS owner_name, own.email AS owner_email, own.department AS owner_dept, own.campus_name AS owner_campus, " +
            "req.full_name AS requester_name, req.email AS requester_email, req.department AS requester_dept, req.campus_name AS requester_campus, " +
            "er.proposed_duration_days ";

    private static final String FROM_JOIN_CLAUSE =
            "FROM exchanges ex " +
            "JOIN books b ON ex.book_id = b.id " +
            "LEFT JOIN books ob ON ex.offered_book_id = ob.id " +
            "JOIN students own ON ex.owner_id = own.id " +
            "JOIN students req ON ex.requester_id = req.id " +
            "LEFT JOIN exchange_requests er ON ex.request_id = er.id ";

    /**
     * Inserts an exchange record inside an active transaction.
     */
    public Long createExchange(Connection conn, Exchange exchange) throws SQLException {
        String sql = "INSERT INTO exchanges (request_id, book_id, offered_book_id, owner_id, requester_id, " +
                     "exchange_mode, status, owner_handover_confirmed, requester_handover_confirmed, " +
                     "owner_return_confirmed, requester_return_confirmed, handover_location, handover_notes, " +
                     "start_date, due_date, return_date) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, exchange.getRequestId());
            stmt.setLong(2, exchange.getBookId());

            if (exchange.getOfferedBookId() != null) {
                stmt.setLong(3, exchange.getOfferedBookId());
            } else {
                stmt.setNull(3, Types.BIGINT);
            }

            stmt.setLong(4, exchange.getOwnerId());
            stmt.setLong(5, exchange.getRequesterId());
            stmt.setString(6, exchange.getExchangeMode());
            stmt.setString(7, exchange.getStatus() != null ? exchange.getStatus() : "AWAITING_HANDOVER");
            stmt.setBoolean(8, exchange.isOwnerHandoverConfirmed());
            stmt.setBoolean(9, exchange.isRequesterHandoverConfirmed());
            stmt.setBoolean(10, exchange.isOwnerReturnConfirmed());
            stmt.setBoolean(11, exchange.isRequesterReturnConfirmed());
            stmt.setString(12, exchange.getHandoverLocation());
            stmt.setString(13, exchange.getHandoverNotes());

            if (exchange.getStartDate() != null) {
                stmt.setDate(14, exchange.getStartDate());
            } else {
                stmt.setNull(14, Types.DATE);
            }

            if (exchange.getDueDate() != null) {
                stmt.setDate(15, exchange.getDueDate());
            } else {
                stmt.setNull(15, Types.DATE);
            }

            if (exchange.getReturnDate() != null) {
                stmt.setDate(16, exchange.getReturnDate());
            } else {
                stmt.setNull(16, Types.DATE);
            }

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Creating exchange record failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long id = generatedKeys.getLong(1);
                    exchange.setId(id);
                    return id;
                } else {
                    throw new SQLException("Creating exchange record failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Finds an exchange by ID with all joined details.
     */
    public Exchange findById(Long id) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        }
    }

    /**
     * Finds an exchange by ID within an existing connection.
     */
    public Exchange findById(Connection conn, Long id) throws SQLException {
        String sql = "SELECT " + SELECT_JOIN_FIELDS + FROM_JOIN_CLAUSE + "WHERE ex.id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToExchange(rs);
                }
            }
        }
        return null;
    }

    /**
     * Finds an exchange by ID with exclusive row-level lock (FOR UPDATE) within a transaction.
     */
    public Exchange findByIdForUpdate(Connection conn, Long id) throws SQLException {
        String sql = "SELECT " + SELECT_JOIN_FIELDS + FROM_JOIN_CLAUSE + "WHERE ex.id = ? FOR UPDATE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToExchange(rs);
                }
            }
        }
        return null;
    }

    /**
     * Finds an exchange associated with a request ID.
     */
    public Exchange findByRequestId(Long requestId) throws SQLException {
        String sql = "SELECT " + SELECT_JOIN_FIELDS + FROM_JOIN_CLAUSE + "WHERE ex.request_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, requestId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToExchange(rs);
                }
            }
        }
        return null;
    }

    /**
     * Finds all exchanges for a given student (whether as owner or requester).
     */
    public List<Exchange> findByStudent(Long studentId) throws SQLException {
        List<Exchange> list = new ArrayList<>();
        String sql = "SELECT " + SELECT_JOIN_FIELDS + FROM_JOIN_CLAUSE +
                     "WHERE ex.owner_id = ? OR ex.requester_id = ? ORDER BY ex.updated_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, studentId);
            stmt.setLong(2, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToExchange(rs));
                }
            }
        }
        return list;
    }

    /**
     * Updates handover confirmation flag for the owner or requester.
     */
    public boolean updateHandoverConfirmation(Connection conn, Long exchangeId, boolean isOwner, boolean isRequester) throws SQLException {
        String sql;
        if (isOwner && isRequester) {
            sql = "UPDATE exchanges SET owner_handover_confirmed = TRUE, requester_handover_confirmed = TRUE WHERE id = ?";
        } else if (isOwner) {
            sql = "UPDATE exchanges SET owner_handover_confirmed = TRUE WHERE id = ?";
        } else if (isRequester) {
            sql = "UPDATE exchanges SET requester_handover_confirmed = TRUE WHERE id = ?";
        } else {
            return false;
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, exchangeId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Updates return confirmation flag for the owner or requester (LENDING mode).
     */
    public boolean updateReturnConfirmation(Connection conn, Long exchangeId, boolean isOwner, boolean isRequester) throws SQLException {
        String sql;
        if (isOwner && isRequester) {
            sql = "UPDATE exchanges SET owner_return_confirmed = TRUE, requester_return_confirmed = TRUE WHERE id = ?";
        } else if (isOwner) {
            sql = "UPDATE exchanges SET owner_return_confirmed = TRUE WHERE id = ?";
        } else if (isRequester) {
            sql = "UPDATE exchanges SET requester_return_confirmed = TRUE WHERE id = ?";
        } else {
            return false;
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, exchangeId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Updates exchange lifecycle status.
     */
    public boolean updateStatus(Connection conn, Long exchangeId, String status) throws SQLException {
        String sql = "UPDATE exchanges SET status = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setLong(2, exchangeId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Updates start date and due date upon handover completion.
     */
    public boolean updateStartDateAndDueDate(Connection conn, Long exchangeId, Date startDate, Date dueDate) throws SQLException {
        String sql = "UPDATE exchanges SET start_date = ?, due_date = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, startDate);
            if (dueDate != null) {
                stmt.setDate(2, dueDate);
            } else {
                stmt.setNull(2, Types.DATE);
            }
            stmt.setLong(3, exchangeId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Updates actual return date upon return confirmation.
     */
    public boolean updateActualReturnDate(Connection conn, Long exchangeId, Date returnDate) throws SQLException {
        String sql = "UPDATE exchanges SET return_date = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, returnDate);
            stmt.setLong(2, exchangeId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Maps a ResultSet row to an Exchange model.
     */
    private Exchange mapRowToExchange(ResultSet rs) throws SQLException {
        Exchange ex = new Exchange();
        ex.setId(rs.getLong("id"));
        ex.setRequestId(rs.getLong("request_id"));
        ex.setBookId(rs.getLong("book_id"));

        long offeredBookId = rs.getLong("offered_book_id");
        if (!rs.wasNull()) {
            ex.setOfferedBookId(offeredBookId);
        }

        ex.setOwnerId(rs.getLong("owner_id"));
        ex.setRequesterId(rs.getLong("requester_id"));
        ex.setExchangeMode(rs.getString("exchange_mode"));
        ex.setStatus(rs.getString("status"));

        ex.setOwnerHandoverConfirmed(rs.getBoolean("owner_handover_confirmed"));
        ex.setRequesterHandoverConfirmed(rs.getBoolean("requester_handover_confirmed"));
        ex.setOwnerReturnConfirmed(rs.getBoolean("owner_return_confirmed"));
        ex.setRequesterReturnConfirmed(rs.getBoolean("requester_return_confirmed"));

        ex.setHandoverLocation(rs.getString("handover_location"));
        ex.setHandoverNotes(rs.getString("handover_notes"));

        ex.setStartDate(rs.getDate("start_date"));
        ex.setDueDate(rs.getDate("due_date"));
        ex.setReturnDate(rs.getDate("return_date"));

        ex.setCreatedAt(rs.getTimestamp("created_at"));
        ex.setUpdatedAt(rs.getTimestamp("updated_at"));

        // Book details
        ex.setBookTitle(rs.getString("book_title"));
        ex.setBookAuthor(rs.getString("book_author"));
        ex.setBookImageUrl(rs.getString("book_image_url"));
        ex.setBookSubject(rs.getString("book_subject"));

        // Offered book details
        ex.setOfferedBookTitle(rs.getString("offered_book_title"));
        ex.setOfferedBookAuthor(rs.getString("offered_book_author"));
        ex.setOfferedBookImageUrl(rs.getString("offered_book_image_url"));

        // Owner details
        ex.setOwnerName(rs.getString("owner_name"));
        ex.setOwnerEmail(rs.getString("owner_email"));
        ex.setOwnerDepartment(rs.getString("owner_dept"));
        ex.setOwnerCampus(rs.getString("owner_campus"));

        // Requester details
        ex.setRequesterName(rs.getString("requester_name"));
        ex.setRequesterEmail(rs.getString("requester_email"));
        ex.setRequesterDepartment(rs.getString("requester_dept"));
        ex.setRequesterCampus(rs.getString("requester_campus"));

        int proposedDays = rs.getInt("proposed_duration_days");
        if (!rs.wasNull()) {
            ex.setProposedDurationDays(proposedDays);
        }

        return ex;
    }
}
