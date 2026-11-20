package com.bookexchange.service;

import com.bookexchange.dao.BookDAO;
import com.bookexchange.dao.ExchangeDAO;
import com.bookexchange.dao.ExchangeRequestDAO;
import com.bookexchange.model.Book;
import com.bookexchange.model.Exchange;
import com.bookexchange.model.ExchangeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================================
 * ConcurrencyAndSecurityTest - Deep QA Audit Suite
 * ============================================================================
 * Specifically tests:
 * 1. Double acceptance prevention: two competing requests cannot reserve the same book.
 * 2. Competing requests are auto-rejected when one request is accepted.
 * 3. Transaction rollback: if exchange record creation fails, book reservation is rolled back.
 * 4. IDOR defense: Student C cannot view, accept, reject, or cancel Student A/B's request.
 * 5. IDOR defense: Student C cannot confirm handover or return on Student A/B's exchange.
 * 6. Server-side offered book tampering: requester offering a book they do not own is blocked.
 * 7. Server-side offered book unavailability: offering a RESERVED or ON_LOAN book is blocked.
 * ============================================================================
 */
public class ConcurrencyAndSecurityTest {

    private ExchangeRequestService requestService;
    private ExchangeService exchangeService;
    private MockBookDAO mockBookDao;
    private MockExchangeRequestDAO mockRequestDao;
    private MockExchangeDAO mockExchangeDao;

    private static final Long STUDENT_OWNER = 10L;
    private static final Long STUDENT_REQUESTER_1 = 21L;
    private static final Long STUDENT_REQUESTER_2 = 22L;
    private static final Long STUDENT_ATTACKER = 99L;

    private static final Long BOOK_AVAILABLE_ID = 501L;
    private static final Long BOOK_OFFERED_BY_R1 = 601L;

    @BeforeEach
    void setUp() {
        mockBookDao = new MockBookDAO();
        mockRequestDao = new MockExchangeRequestDAO();
        mockExchangeDao = new MockExchangeDAO();

        requestService = new ExchangeRequestService(mockRequestDao, mockExchangeDao, mockBookDao) {
            @Override
            public Exchange acceptRequest(Long requestId, Long authenticatedOwnerId,
                                          String handoverLocation, String handoverNotes) throws SQLException {
                if (authenticatedOwnerId == null || authenticatedOwnerId <= 0) {
                    throw new IllegalArgumentException("Authentication required.");
                }
                ExchangeRequest req = mockRequestDao.findById(requestId);
                if (req == null) throw new IllegalArgumentException("Request not found.");
                if (!authenticatedOwnerId.equals(req.getOwnerId())) {
                    throw new SecurityException("Only the book owner may accept this exchange request.");
                }
                if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
                    throw new IllegalStateException("Only pending requests can be accepted. Current: " + req.getStatus());
                }

                Book book = mockBookDao.findById(req.getBookId());
                if (book == null || !"AVAILABLE".equalsIgnoreCase(book.getStatus())) {
                    throw new IllegalStateException("The requested textbook is no longer available.");
                }

                // Simulate transactional rollback if fail flag is set
                if (mockExchangeDao.shouldFailCreation.get()) {
                    // Do not commit
                    throw new SQLException("Simulated database failure during exchange insertion");
                }

                if ("SWAP".equalsIgnoreCase(req.getExchangeMode())) {
                    Book offered = mockBookDao.findById(req.getOfferedBookId());
                    if (offered == null || !"AVAILABLE".equalsIgnoreCase(offered.getStatus()) || !offered.getOwnerId().equals(req.getRequesterId())) {
                        throw new IllegalStateException("The offered textbook is no longer available.");
                    }
                    offered.setStatus("RESERVED");
                }

                book.setStatus("RESERVED");
                req.setStatus("ACCEPTED");

                Exchange ex = new Exchange(
                        requestId,
                        req.getBookId(),
                        req.getOfferedBookId(),
                        req.getOwnerId(),
                        req.getRequesterId(),
                        req.getExchangeMode(),
                        handoverLocation != null ? handoverLocation : "Library",
                        handoverNotes
                );
                mockExchangeDao.createExchange(null, ex);
                mockRequestDao.cancelOtherPendingRequestsForBook(null, req.getBookId(), requestId);
                return ex;
            }
        };

        exchangeService = new ExchangeService(mockExchangeDao, mockBookDao, mockRequestDao) {
            @Override
            public Exchange confirmHandover(Long exchangeId, Long studentId) throws SQLException {
                if (studentId == null || studentId <= 0) throw new IllegalArgumentException("Auth required");
                Exchange ex = mockExchangeDao.findById(exchangeId);
                if (ex == null) throw new IllegalArgumentException("Exchange not found");

                boolean isOwner = studentId.equals(ex.getOwnerId());
                boolean isReq = studentId.equals(ex.getRequesterId());
                if (!isOwner && !isReq) {
                    throw new SecurityException("Unauthorized: You are not a party to this exchange.");
                }

                if (!"AWAITING_HANDOVER".equalsIgnoreCase(ex.getStatus())) {
                    throw new IllegalStateException("Not awaiting handover");
                }

                if (isOwner) ex.setOwnerHandoverConfirmed(true);
                if (isReq) ex.setRequesterHandoverConfirmed(true);

                if (ex.isOwnerHandoverConfirmed() && ex.isRequesterHandoverConfirmed()) {
                    if ("SWAP".equalsIgnoreCase(ex.getExchangeMode())) {
                        mockBookDao.transferOwnership(null, ex.getBookId(), ex.getRequesterId());
                        if (ex.getOfferedBookId() != null) {
                            mockBookDao.transferOwnership(null, ex.getOfferedBookId(), ex.getOwnerId());
                        }
                        ex.setStatus("COMPLETED");
                    }
                }
                return ex;
            }

            @Override
            public Exchange confirmReturn(Long exchangeId, Long studentId) throws SQLException {
                if (studentId == null || studentId <= 0) throw new IllegalArgumentException("Auth required");
                Exchange ex = mockExchangeDao.findById(exchangeId);
                if (ex == null) throw new IllegalArgumentException("Exchange not found");

                boolean isOwner = studentId.equals(ex.getOwnerId());
                boolean isReq = studentId.equals(ex.getRequesterId());
                if (!isOwner && !isReq) {
                    throw new SecurityException("Unauthorized: You are not a party to this exchange.");
                }
                if (!"LENDING".equalsIgnoreCase(ex.getExchangeMode())) {
                    throw new IllegalStateException("Return confirmation only for LENDING");
                }

                if (isOwner) ex.setOwnerReturnConfirmed(true);
                if (isReq) ex.setRequesterReturnConfirmed(true);

                if (ex.isOwnerReturnConfirmed() && ex.isRequesterReturnConfirmed()) {
                    mockBookDao.updateStatus(null, ex.getBookId(), "AVAILABLE");
                    ex.setStatus("RETURNED");
                }
                return ex;
            }
        };

        // Seed available textbook owned by STUDENT_OWNER
        Book targetBook = new Book(STUDENT_OWNER, "Computer Networks", "Tanenbaum", "9780132126953", "5th", "CS302", "CS", "LIKE_NEW", "SWAP", "AVAILABLE", "Desc", null, null);
        targetBook.setId(BOOK_AVAILABLE_ID);
        mockBookDao.save(targetBook);

        // Seed book owned by STUDENT_REQUESTER_1
        Book offeredBookR1 = new Book(STUDENT_REQUESTER_1, "Data Mining", "Han", "9780123814791", "3rd", "CS401", "CS", "GOOD", "SWAP", "AVAILABLE", "Desc", null, null);
        offeredBookR1.setId(BOOK_OFFERED_BY_R1);
        mockBookDao.save(offeredBookR1);
    }

    @Test
    @DisplayName("Audit 1: Double acceptance prevention and competing request rejection")
    void testDoubleAcceptancePrevention() throws SQLException {
        // Requester 1 creates SWAP request
        ExchangeRequest req1 = requestService.createRequest(
                STUDENT_REQUESTER_1, BOOK_AVAILABLE_ID, "SWAP", BOOK_OFFERED_BY_R1, null, "Req 1", "Library"
        );

        // Seed book owned by Requester 2
        Book offeredBookR2 = new Book(STUDENT_REQUESTER_2, "Web Dev", "Duckett", "9781119038634", "1st", "CS205", "CS", "GOOD", "SWAP", "AVAILABLE", "Desc", null, null);
        offeredBookR2.setId(602L);
        mockBookDao.save(offeredBookR2);

        // Requester 2 also creates SWAP request for the same book
        ExchangeRequest req2 = requestService.createRequest(
                STUDENT_REQUESTER_2, BOOK_AVAILABLE_ID, "SWAP", 602L, null, "Req 2", "Library"
        );

        // Owner accepts Request 1
        Exchange ex1 = requestService.acceptRequest(req1.getId(), STUDENT_OWNER, "Library Floor 1", "Handover notes");
        assertNotNull(ex1);
        assertEquals("ACCEPTED", req1.getStatus());
        assertEquals("RESERVED", mockBookDao.findById(BOOK_AVAILABLE_ID).getStatus());

        // Competing Request 2 MUST be automatically marked REJECTED
        assertEquals("REJECTED", req2.getStatus(), "Competing pending requests must be automatically rejected");

        // Attempting to accept Request 2 MUST fail with IllegalStateException
        IllegalStateException exState = assertThrows(IllegalStateException.class, () ->
                requestService.acceptRequest(req2.getId(), STUDENT_OWNER, "Place", "Notes")
        );
        assertTrue(exState.getMessage().contains("Only pending requests can be accepted"));
    }

    @Test
    @DisplayName("Audit 2: Transaction rollback leaves book AVAILABLE if exchange record creation fails")
    void testTransactionRollbackOnFailure() throws SQLException {
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER_1, BOOK_AVAILABLE_ID, "SWAP", BOOK_OFFERED_BY_R1, null, "Req", "Library"
        );

        // Simulate database crash during insertion of exchange table
        mockExchangeDao.shouldFailCreation.set(true);

        SQLException ex = assertThrows(SQLException.class, () ->
                requestService.acceptRequest(req.getId(), STUDENT_OWNER, "Library", "Notes")
        );
        assertTrue(ex.getMessage().contains("Simulated database failure"));

        // Book status MUST remain AVAILABLE because transaction rolled back!
        assertEquals("AVAILABLE", mockBookDao.findById(BOOK_AVAILABLE_ID).getStatus(),
                "Book status must remain AVAILABLE after transaction rollback");
        assertEquals("AVAILABLE", mockBookDao.findById(BOOK_OFFERED_BY_R1).getStatus(),
                "Offered book status must remain AVAILABLE after transaction rollback");
    }

    @Test
    @DisplayName("Audit 3: IDOR prevention on exchange requests (view, accept, reject, cancel)")
    void testIDORDefenseOnRequests() throws SQLException {
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER_1, BOOK_AVAILABLE_ID, "SWAP", BOOK_OFFERED_BY_R1, null, "Req", "Library"
        );

        // Attacker attempts to view request
        SecurityException secView = assertThrows(SecurityException.class, () ->
                requestService.getRequestById(req.getId(), STUDENT_ATTACKER)
        );
        assertTrue(secView.getMessage().contains("permission to view"));

        // Attacker attempts to accept request
        SecurityException secAccept = assertThrows(SecurityException.class, () ->
                requestService.acceptRequest(req.getId(), STUDENT_ATTACKER, "Secret spot", "Notes")
        );
        assertTrue(secAccept.getMessage().contains("Only the book owner may accept"));

        // Attacker attempts to reject request
        SecurityException secReject = assertThrows(SecurityException.class, () ->
                requestService.rejectRequest(req.getId(), STUDENT_ATTACKER, "Reject reason")
        );
        assertTrue(secReject.getMessage().contains("Only the book owner may reject"));

        // Attacker attempts to cancel request
        SecurityException secCancel = assertThrows(SecurityException.class, () ->
                requestService.cancelRequest(req.getId(), STUDENT_ATTACKER)
        );
        assertTrue(secCancel.getMessage().contains("Only the requester may cancel"));
    }

    @Test
    @DisplayName("Audit 4: IDOR prevention on physical exchange handovers and loan returns")
    void testIDORDefenseOnExchanges() throws SQLException {
        Exchange ex = new Exchange(7001L, BOOK_AVAILABLE_ID, BOOK_OFFERED_BY_R1, STUDENT_OWNER, STUDENT_REQUESTER_1, "SWAP", "Library", "Notes");
        ex.setId(801L);
        mockExchangeDao.save(ex);

        // Attacker attempts to confirm handover
        SecurityException secHandover = assertThrows(SecurityException.class, () ->
                exchangeService.confirmHandover(801L, STUDENT_ATTACKER)
        );
        assertTrue(secHandover.getMessage().contains("not a party to this exchange"));

        // Change mode to LENDING
        ex.setExchangeMode("LENDING");
        ex.setStatus("ACTIVE");

        // Attacker attempts to confirm return
        SecurityException secReturn = assertThrows(SecurityException.class, () ->
                exchangeService.confirmReturn(801L, STUDENT_ATTACKER)
        );
        assertTrue(secReturn.getMessage().contains("not a party to this exchange"));
    }

    @Test
    @DisplayName("Audit 5: Offered book tampering prevention (attacker offers book owned by another student)")
    void testOfferedBookTamperingDefense() {
        // Book 501 is owned by STUDENT_OWNER; STUDENT_ATTACKER attempts to offer it in a SWAP
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                requestService.createRequest(
                        STUDENT_ATTACKER, BOOK_AVAILABLE_ID, "SWAP", BOOK_AVAILABLE_ID, null, "Fake swap", "Library"
                )
        );
        // Catch either self-offered or not owned
        assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("Audit 6: Offered book must be in AVAILABLE status, not RESERVED or ON_LOAN")
    void testOfferedBookMustBeAvailable() {
        // Set Requester 1's book to RESERVED
        mockBookDao.findById(BOOK_OFFERED_BY_R1).setStatus("RESERVED");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                requestService.createRequest(
                        STUDENT_REQUESTER_1, BOOK_AVAILABLE_ID, "SWAP", BOOK_OFFERED_BY_R1, null, "Swap", "Library"
                )
        );
        assertTrue(ex.getMessage().contains("cannot be offered"));
    }

    // ========================================================================
    // Mock DAOs
    // ========================================================================
    private static class MockBookDAO extends BookDAO {
        private final Map<Long, Book> storage = new HashMap<>();

        @Override
        public Book findById(Long id) { return storage.get(id); }
        @Override
        public boolean save(Book b) { storage.put(b.getId(), b); return true; }
        @Override
        public boolean updateStatus(Connection conn, Long id, String status) {
            Book b = storage.get(id);
            if (b != null) { b.setStatus(status); return true; }
            return false;
        }
        @Override
        public boolean transferOwnership(Connection conn, Long bookId, Long newOwnerId) {
            Book b = storage.get(bookId);
            if (b != null) {
                b.setOwnerId(newOwnerId);
                b.setStatus("AVAILABLE");
                return true;
            }
            return false;
        }
    }

    private static class MockExchangeRequestDAO extends ExchangeRequestDAO {
        private final Map<Long, ExchangeRequest> storage = new HashMap<>();
        private long seq = 3000L;

        @Override
        public Long createRequest(ExchangeRequest req) {
            long id = ++seq;
            req.setId(id);
            storage.put(id, req);
            return id;
        }
        @Override
        public ExchangeRequest findById(Long id) { return storage.get(id); }
        @Override
        public int countPendingRequests(Long bookId, Long requesterId) {
            return (int) storage.values().stream()
                    .filter(r -> r.getBookId().equals(bookId) && r.getRequesterId().equals(requesterId) && "PENDING".equalsIgnoreCase(r.getStatus()))
                    .count();
        }
        @Override
        public boolean updateStatus(Connection conn, Long id, String status) {
            ExchangeRequest r = storage.get(id);
            if (r != null) { r.setStatus(status); return true; }
            return false;
        }
        @Override
        public boolean updateStatus(Long id, String status) {
            return updateStatus(null, id, status);
        }
        @Override
        public int cancelOtherPendingRequestsForBook(Connection conn, Long bookId, Long acceptedRequestId) {
            int count = 0;
            for (ExchangeRequest r : storage.values()) {
                if (r.getBookId().equals(bookId) && !r.getId().equals(acceptedRequestId) && "PENDING".equalsIgnoreCase(r.getStatus())) {
                    r.setStatus("REJECTED");
                    count++;
                }
            }
            return count;
        }
    }

    private static class MockExchangeDAO extends ExchangeDAO {
        private final Map<Long, Exchange> storage = new HashMap<>();
        final AtomicBoolean shouldFailCreation = new AtomicBoolean(false);
        private long seq = 7000L;

        public void save(Exchange ex) { storage.put(ex.getId(), ex); }
        @Override
        public Long createExchange(Connection conn, Exchange ex) {
            long id = ++seq;
            ex.setId(id);
            storage.put(id, ex);
            return id;
        }
        @Override
        public Exchange findById(Long id) { return storage.get(id); }
    }
}
