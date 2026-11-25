package com.bookexchange.service;

import com.bookexchange.config.DBConnection;
import com.bookexchange.dao.BookDAO;
import com.bookexchange.dao.ExchangeDAO;
import com.bookexchange.dao.ExchangeRequestDAO;
import com.bookexchange.model.Book;
import com.bookexchange.model.Exchange;
import com.bookexchange.model.ExchangeRequest;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * ============================================================================
 * ExchangeRequestService - Core Business Logic for Exchange Requests
 * ============================================================================
 * Enforces business rules:
 * 1. Self-request is strictly prohibited (students cannot request their own books).
 * 2. Only AVAILABLE book listings can receive requests.
 * 3. Mode matching: SWAP, GIVEAWAY, and LENDING validation rules.
 * 4. SWAP requires an offered book owned by the requester and in AVAILABLE status.
 * 5. Offered book cannot be the same as the requested book.
 * 6. LENDING requires a valid positive duration <= max duration.
 * 7. Duplicate pending requests are prevented.
 * 8. Only the book owner can accept or reject a request.
 * 9. Only the requester can cancel a pending request.
 * 10. Acceptance is fully transactional with row-level locks (SELECT FOR UPDATE)
 *     to prevent race conditions / double acceptance.
 * 11. Competing pending requests for the same book are automatically rejected on accept.
 * ============================================================================
 */
public class ExchangeRequestService {

    private final ExchangeRequestDAO exchangeRequestDAO;
    private final ExchangeDAO exchangeDAO;
    private final BookDAO bookDAO;

    public ExchangeRequestService() {
        this.exchangeRequestDAO = new ExchangeRequestDAO();
        this.exchangeDAO = new ExchangeDAO();
        this.bookDAO = new BookDAO();
    }

    public ExchangeRequestService(ExchangeRequestDAO exchangeRequestDAO, ExchangeDAO exchangeDAO, BookDAO bookDAO) {
        this.exchangeRequestDAO = exchangeRequestDAO;
        this.exchangeDAO = exchangeDAO;
        this.bookDAO = bookDAO;
    }

    /**
     * Submits a new student peer-to-peer book exchange request.
     */
    public ExchangeRequest createRequest(Long studentId, Long bookId, String exchangeMode,
                                         Long offeredBookId, Integer proposedDurationDays,
                                         String requestMessage, String handoverLocation) throws SQLException {

        if (studentId == null || studentId <= 0) {
            throw new IllegalArgumentException("Authentication required to submit an exchange request.");
        }
        if (bookId == null || bookId <= 0) {
            throw new IllegalArgumentException("Target book ID is required.");
        }

        try (Connection conn = DBConnection.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // Lock book listing to prevent concurrent duplicate race conditions
                Book book = bookDAO.findByIdForUpdate(conn, bookId);
                if (book == null) {
                    throw new IllegalArgumentException("Requested textbook listing not found.");
                }

                // Rule 1: A student cannot request their own book
                if (studentId.equals(book.getOwnerId())) {
                    throw new IllegalArgumentException("You cannot request your own textbook listing.");
                }

                // Rule 2: Only AVAILABLE listings can receive requests
                if (!"AVAILABLE".equalsIgnoreCase(book.getStatus())) {
                    throw new IllegalStateException("This book listing is currently " + book.getStatus() + " and not accepting requests.");
                }

                // Resolve sharing mode
                String mode = (exchangeMode != null && !exchangeMode.isBlank()) ? exchangeMode.toUpperCase().trim() : book.getSharingMode();
                if (!"SWAP".equals(mode) && !"GIVEAWAY".equals(mode) && !"LENDING".equals(mode)) {
                    throw new IllegalArgumentException("Invalid sharing mode: " + mode);
                }

                // Rule 3: Check duplicate pending requests under row-lock protection
                int existingCount = exchangeRequestDAO.countPendingRequests(conn, bookId, studentId);
                if (existingCount > 0) {
                    throw new IllegalStateException("You already have an active pending request for this book. Please await owner review.");
                }

                // Rule 4: SWAP mode validations
                if ("SWAP".equals(mode)) {
                    if (offeredBookId == null || offeredBookId <= 0) {
                        throw new IllegalArgumentException("You must select one of your own available textbooks to offer in a SWAP exchange.");
                    }
                    if (offeredBookId.equals(bookId)) {
                        throw new IllegalArgumentException("The offered book cannot be the same as the requested book.");
                    }

                    Book offeredBook = bookDAO.findByIdForUpdate(conn, offeredBookId);
                    if (offeredBook == null) {
                        throw new IllegalArgumentException("The offered textbook listing was not found.");
                    }
                    if (!studentId.equals(offeredBook.getOwnerId())) {
                        throw new IllegalArgumentException("The offered book does not belong to you.");
                    }
                    if (!"AVAILABLE".equalsIgnoreCase(offeredBook.getStatus())) {
                        throw new IllegalStateException("Your offered book is currently " + offeredBook.getStatus() + " and cannot be offered.");
                    }
                } else {
                    offeredBookId = null; // No offered book for GIVEAWAY or LENDING
                }

                // Rule 5: LENDING mode validations
                if ("LENDING".equals(mode)) {
                    if (proposedDurationDays == null || proposedDurationDays <= 0) {
                        throw new IllegalArgumentException("A valid positive loan duration (in days) must be specified for LENDING.");
                    }
                    if (book.getLendingMaxDurationDays() != null && proposedDurationDays > book.getLendingMaxDurationDays()) {
                        throw new IllegalArgumentException("Proposed loan duration (" + proposedDurationDays + " days) exceeds the owner's limit of " +
                                book.getLendingMaxDurationDays() + " days.");
                    }
                } else {
                    proposedDurationDays = null;
                }

                String location = (handoverLocation != null && !handoverLocation.isBlank())
                        ? handoverLocation.trim()
                        : book.getOwnerCampus();

                ExchangeRequest req = new ExchangeRequest(
                        bookId,
                        studentId,
                        book.getOwnerId(),
                        mode,
                        offeredBookId,
                        proposedDurationDays,
                        requestMessage != null ? requestMessage.trim() : null,
                        location
                );

                Long requestId = exchangeRequestDAO.createRequest(conn, req);
                req.setId(requestId);
                conn.commit();
                return exchangeRequestDAO.findById(conn, requestId);

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }

    /**
     * Accepts an exchange request within a transactional JDBC block.
     * Locks both requested and offered books using SELECT FOR UPDATE to prevent race conditions.
     */
    public Exchange acceptRequest(Long requestId, Long authenticatedOwnerId,
                                  String handoverLocation, String handoverNotes) throws SQLException {

        if (authenticatedOwnerId == null || authenticatedOwnerId <= 0) {
            throw new IllegalArgumentException("Authentication required to accept requests.");
        }
        if (requestId == null || requestId <= 0) {
            throw new IllegalArgumentException("Request ID is required.");
        }

        try (Connection conn = DBConnection.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // 1. Lock and load the request
                ExchangeRequest req = exchangeRequestDAO.findByIdForUpdate(conn, requestId);
                if (req == null) {
                    throw new IllegalArgumentException("Exchange request not found.");
                }

                // 2. Verify ownership
                if (!authenticatedOwnerId.equals(req.getOwnerId())) {
                    throw new SecurityException("Only the book owner may accept this exchange request.");
                }

                // 3. Verify status is PENDING
                if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
                    throw new IllegalStateException("Only pending requests can be accepted. Current status: " + req.getStatus());
                }

                // 4. Lock requested book and verify availability
                Book book = bookDAO.findByIdForUpdate(conn, req.getBookId());
                if (book == null || !"AVAILABLE".equalsIgnoreCase(book.getStatus())) {
                    throw new IllegalStateException("The requested textbook is no longer available.");
                }

                // 5. If SWAP, lock offered book and verify availability
                if ("SWAP".equalsIgnoreCase(req.getExchangeMode())) {
                    if (req.getOfferedBookId() == null) {
                        throw new IllegalStateException("SWAP request is missing an offered book.");
                    }
                    Book offeredBook = bookDAO.findByIdForUpdate(conn, req.getOfferedBookId());
                    if (offeredBook == null || !"AVAILABLE".equalsIgnoreCase(offeredBook.getStatus())
                            || !offeredBook.getOwnerId().equals(req.getRequesterId())) {
                        throw new IllegalStateException("The offered textbook is no longer available from the requester.");
                    }
                    // Reserve offered book
                    bookDAO.updateStatus(conn, offeredBook.getId(), "RESERVED");
                }

                // 6. Reserve requested book
                bookDAO.updateStatus(conn, book.getId(), "RESERVED");

                // 7. Update request status to ACCEPTED
                exchangeRequestDAO.updateStatus(conn, requestId, "ACCEPTED");

                // 8. Create Exchange record in AWAITING_HANDOVER status
                String location = (handoverLocation != null && !handoverLocation.isBlank())
                        ? handoverLocation.trim()
                        : (req.getHandoverLocation() != null ? req.getHandoverLocation() : book.getOwnerCampus());

                Exchange exchange = new Exchange(
                        requestId,
                        req.getBookId(),
                        req.getOfferedBookId(),
                        req.getOwnerId(),
                        req.getRequesterId(),
                        req.getExchangeMode(),
                        location,
                        handoverNotes
                );

                Long exchangeId = exchangeDAO.createExchange(conn, exchange);
                exchange.setId(exchangeId);

                // 9. Automatically reject other competing pending requests for this book
                exchangeRequestDAO.cancelOtherPendingRequestsForBook(conn, req.getBookId(), requestId);

                conn.commit();
                return exchangeDAO.findById(conn, exchangeId);

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }

    /**
     * Declines a pending request. Only the book owner may reject.
     */
    public boolean rejectRequest(Long requestId, Long authenticatedOwnerId, String reason) throws SQLException {
        if (authenticatedOwnerId == null || authenticatedOwnerId <= 0) {
            throw new IllegalArgumentException("Authentication required.");
        }

        ExchangeRequest req = exchangeRequestDAO.findById(requestId);
        if (req == null) {
            throw new IllegalArgumentException("Exchange request not found.");
        }
        if (!authenticatedOwnerId.equals(req.getOwnerId())) {
            throw new SecurityException("Only the book owner may reject this exchange request.");
        }
        if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
            throw new IllegalStateException("Only pending requests can be rejected. Current status: " + req.getStatus());
        }

        return exchangeRequestDAO.updateStatus(requestId, "REJECTED");
    }

    /**
     * Cancels a pending request. Only the student who submitted the request may cancel.
     */
    public boolean cancelRequest(Long requestId, Long authenticatedRequesterId) throws SQLException {
        if (authenticatedRequesterId == null || authenticatedRequesterId <= 0) {
            throw new IllegalArgumentException("Authentication required.");
        }

        ExchangeRequest req = exchangeRequestDAO.findById(requestId);
        if (req == null) {
            throw new IllegalArgumentException("Exchange request not found.");
        }
        if (!authenticatedRequesterId.equals(req.getRequesterId())) {
            throw new SecurityException("Only the requester may cancel their pending request.");
        }
        if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
            throw new IllegalStateException("Only pending requests can be cancelled. Current status: " + req.getStatus());
        }

        return exchangeRequestDAO.updateStatus(requestId, "CANCELLED");
    }

    /**
     * Retrieves all requests sent by the student.
     */
    public List<ExchangeRequest> getSentRequests(Long studentId) throws SQLException {
        if (studentId == null || studentId <= 0) {
            throw new IllegalArgumentException("Valid student ID required.");
        }
        return exchangeRequestDAO.findByRequester(studentId);
    }

    /**
     * Retrieves all requests received by the book owner.
     */
    public List<ExchangeRequest> getReceivedRequests(Long ownerId) throws SQLException {
        if (ownerId == null || ownerId <= 0) {
            throw new IllegalArgumentException("Valid student ID required.");
        }
        return exchangeRequestDAO.findByOwner(ownerId);
    }

    /**
     * Retrieves details of a specific request. Only the requester or owner is permitted.
     */
    public ExchangeRequest getRequestById(Long requestId, Long currentUserId) throws SQLException {
        if (requestId == null || requestId <= 0) {
            throw new IllegalArgumentException("Request ID required.");
        }
        ExchangeRequest req = exchangeRequestDAO.findById(requestId);
        if (req == null) {
            return null;
        }
        if (currentUserId != null && !currentUserId.equals(req.getRequesterId()) && !currentUserId.equals(req.getOwnerId())) {
            throw new SecurityException("You do not have permission to view this exchange request.");
        }
        return req;
    }
}
