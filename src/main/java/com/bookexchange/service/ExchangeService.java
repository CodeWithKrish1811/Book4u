package com.bookexchange.service;

import com.bookexchange.config.DBConnection;
import com.bookexchange.dao.BookDAO;
import com.bookexchange.dao.ExchangeDAO;
import com.bookexchange.dao.ExchangeRequestDAO;
import com.bookexchange.model.Exchange;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.util.List;

/**
 * ============================================================================
 * ExchangeService - Core Business Logic for Physical Handover & Loan Tracking
 * ============================================================================
 * Enforces business rules:
 * 1. Physical handover requires dual confirmation (both owner and requester).
 * 2. An exchange is NEVER marked completed immediately upon request acceptance.
 * 3. After dual handover confirmation:
 *    - SWAP: Transactionally transfer ownership of both books; exchange becomes COMPLETED.
 *    - GIVEAWAY: Transactionally transfer ownership to requester; exchange becomes COMPLETED.
 *    - LENDING: Owner retains ownership; book status becomes ON_LOAN; exchange becomes ACTIVE with due date.
 * 4. For LENDING, return also requires dual confirmation (both parties confirm book returned):
 *    - After dual return confirmation: Book status is restored to AVAILABLE; exchange becomes RETURNED.
 * 5. All state changes are executed with row-level locks and transactional rollbacks.
 * ============================================================================
 */
public class ExchangeService {

    private final ExchangeDAO exchangeDAO;
    private final BookDAO bookDAO;
    private final ExchangeRequestDAO exchangeRequestDAO;

    public ExchangeService() {
        this.exchangeDAO = new ExchangeDAO();
        this.bookDAO = new BookDAO();
        this.exchangeRequestDAO = new ExchangeRequestDAO();
    }

    public ExchangeService(ExchangeDAO exchangeDAO, BookDAO bookDAO, ExchangeRequestDAO exchangeRequestDAO) {
        this.exchangeDAO = exchangeDAO;
        this.bookDAO = bookDAO;
        this.exchangeRequestDAO = exchangeRequestDAO;
    }

    /**
     * Retrieves all active and completed exchanges for the authenticated student.
     */
    public List<Exchange> getStudentExchanges(Long studentId) throws SQLException {
        if (studentId == null || studentId <= 0) {
            throw new IllegalArgumentException("Valid student ID required.");
        }
        return exchangeDAO.findByStudent(studentId);
    }

    /**
     * Retrieves a single exchange by ID with authorization verification.
     */
    public Exchange getExchangeById(Long exchangeId, Long studentId) throws SQLException {
        if (exchangeId == null || exchangeId <= 0) {
            throw new IllegalArgumentException("Exchange ID required.");
        }
        Exchange ex = exchangeDAO.findById(exchangeId);
        if (ex == null) {
            return null;
        }
        if (studentId != null && !studentId.equals(ex.getOwnerId()) && !studentId.equals(ex.getRequesterId())) {
            throw new SecurityException("You do not have permission to view this exchange transaction.");
        }
        return ex;
    }

    /**
     * Confirms physical book handover by the current student.
     * When BOTH students confirm handover, triggers transactional ownership transfer or loan activation.
     */
    public Exchange confirmHandover(Long exchangeId, Long studentId) throws SQLException {
        if (studentId == null || studentId <= 0) {
            throw new IllegalArgumentException("Authentication required.");
        }
        if (exchangeId == null || exchangeId <= 0) {
            throw new IllegalArgumentException("Exchange ID required.");
        }

        try (Connection conn = DBConnection.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // 1. Lock and load exchange
                Exchange ex = exchangeDAO.findByIdForUpdate(conn, exchangeId);
                if (ex == null) {
                    throw new IllegalArgumentException("Exchange record not found.");
                }

                boolean isOwner = studentId.equals(ex.getOwnerId());
                boolean isRequester = studentId.equals(ex.getRequesterId());
                if (!isOwner && !isRequester) {
                    throw new SecurityException("Unauthorized: You are not a party to this exchange.");
                }

                if (!"AWAITING_HANDOVER".equalsIgnoreCase(ex.getStatus())) {
                    throw new IllegalStateException("Handover can only be confirmed when awaiting handover. Current status: " + ex.getStatus());
                }

                // 2. Update current student's confirmation flag
                exchangeDAO.updateHandoverConfirmation(conn, exchangeId, isOwner, isRequester);

                boolean ownerDone = isOwner || ex.isOwnerHandoverConfirmed();
                boolean requesterDone = isRequester || ex.isRequesterHandoverConfirmed();

                // 3. If BOTH have confirmed, finalize the physical exchange
                if (ownerDone && requesterDone) {
                    Date today = new Date(System.currentTimeMillis());

                    if ("SWAP".equalsIgnoreCase(ex.getExchangeMode())) {
                        // SWAP: Transfer both books and restore to AVAILABLE
                        bookDAO.transferOwnership(conn, ex.getBookId(), ex.getRequesterId());
                        if (ex.getOfferedBookId() != null) {
                            bookDAO.transferOwnership(conn, ex.getOfferedBookId(), ex.getOwnerId());
                        }
                        exchangeDAO.updateStatus(conn, exchangeId, "COMPLETED");
                        exchangeDAO.updateStartDateAndDueDate(conn, exchangeId, today, null);

                    } else if ("GIVEAWAY".equalsIgnoreCase(ex.getExchangeMode())) {
                        // GIVEAWAY: Transfer book to recipient and restore to AVAILABLE
                        bookDAO.transferOwnership(conn, ex.getBookId(), ex.getRequesterId());
                        exchangeDAO.updateStatus(conn, exchangeId, "COMPLETED");
                        exchangeDAO.updateStartDateAndDueDate(conn, exchangeId, today, null);

                    } else if ("LENDING".equalsIgnoreCase(ex.getExchangeMode())) {
                        // LENDING: Owner retains ownership, status becomes ON_LOAN, calculate due date
                        bookDAO.updateStatus(conn, ex.getBookId(), "ON_LOAN");
                        exchangeDAO.updateStatus(conn, exchangeId, "ACTIVE");

                        int durationDays = (ex.getProposedDurationDays() != null && ex.getProposedDurationDays() > 0)
                                ? ex.getProposedDurationDays()
                                : 14;

                        long dueMillis = System.currentTimeMillis() + (durationDays * 86400000L);
                        Date dueDate = new Date(dueMillis);

                        exchangeDAO.updateStartDateAndDueDate(conn, exchangeId, today, dueDate);
                    }
                }

                conn.commit();
                return exchangeDAO.findById(exchangeId);

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }

    /**
     * Confirms physical return of a borrowed textbook (for LENDING mode).
     * When BOTH students confirm return, marks exchange RETURNED and restores book to AVAILABLE.
     */
    public Exchange confirmReturn(Long exchangeId, Long studentId) throws SQLException {
        if (studentId == null || studentId <= 0) {
            throw new IllegalArgumentException("Authentication required.");
        }
        if (exchangeId == null || exchangeId <= 0) {
            throw new IllegalArgumentException("Exchange ID required.");
        }

        try (Connection conn = DBConnection.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // 1. Lock and load exchange
                Exchange ex = exchangeDAO.findByIdForUpdate(conn, exchangeId);
                if (ex == null) {
                    throw new IllegalArgumentException("Exchange record not found.");
                }

                boolean isOwner = studentId.equals(ex.getOwnerId());
                boolean isRequester = studentId.equals(ex.getRequesterId());
                if (!isOwner && !isRequester) {
                    throw new SecurityException("Unauthorized: You are not a party to this exchange.");
                }

                if (!"LENDING".equalsIgnoreCase(ex.getExchangeMode())) {
                    throw new IllegalStateException("Return confirmation is only applicable to LENDING exchanges.");
                }

                if (!"ACTIVE".equalsIgnoreCase(ex.getStatus()) && !"OVERDUE".equalsIgnoreCase(ex.getStatus())) {
                    throw new IllegalStateException("Return confirmation requires an ACTIVE or OVERDUE loan. Current status: " + ex.getStatus());
                }

                // 2. Update current student's return confirmation flag
                exchangeDAO.updateReturnConfirmation(conn, exchangeId, isOwner, isRequester);

                boolean ownerDone = isOwner || ex.isOwnerReturnConfirmed();
                boolean requesterDone = isRequester || ex.isRequesterReturnConfirmed();

                // 3. If BOTH confirmed return, restore book to AVAILABLE and mark exchange RETURNED
                if (ownerDone && requesterDone) {
                    bookDAO.updateStatus(conn, ex.getBookId(), "AVAILABLE");
                    exchangeDAO.updateStatus(conn, exchangeId, "RETURNED");
                    Date today = new Date(System.currentTimeMillis());
                    exchangeDAO.updateActualReturnDate(conn, exchangeId, today);
                }

                conn.commit();
                return exchangeDAO.findById(exchangeId);

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }
}
