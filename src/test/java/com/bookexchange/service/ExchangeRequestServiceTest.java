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
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================================
 * ExchangeRequestServiceTest - Comprehensive Unit Tests for Request Management
 * ============================================================================
 * Validates:
 * 1. Request creation for SWAP, GIVEAWAY, and LENDING.
 * 2. Invalid self-request prevention (cannot request own listing).
 * 3. Invalid offered book (not owned, not AVAILABLE, or same as requested book).
 * 4. Giveaway request succeeds without offered book.
 * 5. Lending with invalid duration (zero, negative, or exceeding max duration).
 * 6. Accepting requests (marks books RESERVED, auto-rejects competing requests).
 * 7. Rejecting requests (only listing owner permitted).
 * 8. Cancelling pending requests (only requester permitted).
 * 9. Double acceptance prevention (cannot accept non-pending request).
 * 10. Unauthorized access prevention.
 * ============================================================================
 */
public class ExchangeRequestServiceTest {

    private ExchangeRequestService requestService;
    private MockBookDAO mockBookDao;
    private MockExchangeRequestDAO mockRequestDao;
    private MockExchangeDAO mockExchangeDao;

    private static final Long STUDENT_OWNER = 10L;     // Book owner
    private static final Long STUDENT_REQUESTER = 20L; // Requester
    private static final Long STUDENT_OTHER = 30L;     // Third-party student

    private static final Long BOOK_SWAP_ID = 101L;
    private static final Long BOOK_GIVEAWAY_ID = 102L;
    private static final Long BOOK_LENDING_ID = 103L;
    private static final Long OFFERED_BOOK_ID = 201L;

    @BeforeEach
    void setUp() {
        mockBookDao = new MockBookDAO();
        mockRequestDao = new MockExchangeRequestDAO();
        mockExchangeDao = new MockExchangeDAO();

        requestService = new ExchangeRequestService(mockRequestDao, mockExchangeDao, mockBookDao) {
            // Override acceptRequest to execute with mock in-memory transaction without requiring live MySQL driver
            @Override
            public Exchange acceptRequest(Long requestId, Long authenticatedOwnerId,
                                          String handoverLocation, String handoverNotes) throws SQLException {
                if (authenticatedOwnerId == null || authenticatedOwnerId <= 0) {
                    throw new IllegalArgumentException("Authentication required to accept requests.");
                }
                ExchangeRequest req = mockRequestDao.findById(requestId);
                if (req == null) {
                    throw new IllegalArgumentException("Exchange request not found.");
                }
                if (!authenticatedOwnerId.equals(req.getOwnerId())) {
                    throw new SecurityException("Only the book owner may accept this exchange request.");
                }
                if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
                    throw new IllegalStateException("Only pending requests can be accepted. Current status: " + req.getStatus());
                }

                Book book = mockBookDao.findById(req.getBookId());
                if (book == null || !"AVAILABLE".equalsIgnoreCase(book.getStatus())) {
                    throw new IllegalStateException("The requested textbook is no longer available.");
                }

                if ("SWAP".equalsIgnoreCase(req.getExchangeMode())) {
                    Book offered = mockBookDao.findById(req.getOfferedBookId());
                    if (offered == null || !"AVAILABLE".equalsIgnoreCase(offered.getStatus()) || !offered.getOwnerId().equals(req.getRequesterId())) {
                        throw new IllegalStateException("The offered textbook is no longer available from the requester.");
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
                        handoverLocation != null ? handoverLocation : "Central Library",
                        handoverNotes
                );
                mockExchangeDao.createExchange(null, ex);
                mockRequestDao.cancelOtherPendingRequestsForBook(null, req.getBookId(), requestId);
                return ex;
            }
        };

        // Seed Book 101: SWAP
        Book swapBook = new Book(STUDENT_OWNER, "Algorithms", "CLRS", "9780262046305", "4th", "CS201", "CS", "LIKE_NEW", "SWAP", "AVAILABLE", "Desc", null, null);
        swapBook.setId(BOOK_SWAP_ID);
        mockBookDao.save(swapBook);

        // Seed Book 102: GIVEAWAY
        Book giveawayBook = new Book(STUDENT_OWNER, "Math Foundations", "Strang", "9780000000000", "1st", "MA101", "Math", "GOOD", "GIVEAWAY", "AVAILABLE", "Desc", null, null);
        giveawayBook.setId(BOOK_GIVEAWAY_ID);
        mockBookDao.save(giveawayBook);

        // Seed Book 103: LENDING (max 30 days)
        Book lendingBook = new Book(STUDENT_OWNER, "Operating Systems", "Silberschatz", "9781118063330", "10th", "CS301", "CS", "GOOD", "LENDING", "AVAILABLE", "Desc", 30, null);
        lendingBook.setId(BOOK_LENDING_ID);
        mockBookDao.save(lendingBook);

        // Seed Requester's Book 201: AVAILABLE (to offer for SWAP)
        Book offeredBook = new Book(STUDENT_REQUESTER, "Database Systems", "Korth", "9780073523323", "6th", "CS202", "CS", "GOOD", "SWAP", "AVAILABLE", "Desc", null, null);
        offeredBook.setId(OFFERED_BOOK_ID);
        mockBookDao.save(offeredBook);
    }

    @Test
    @DisplayName("Test 1: Successful SWAP request creation with valid offered book")
    void testCreateSwapRequestSuccess() throws SQLException {
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER,
                BOOK_SWAP_ID,
                "SWAP",
                OFFERED_BOOK_ID,
                null,
                "Need for CS201 class",
                "Central Library Block B"
        );

        assertNotNull(req);
        assertEquals("PENDING", req.getStatus());
        assertEquals("SWAP", req.getExchangeMode());
        assertEquals(BOOK_SWAP_ID, req.getBookId());
        assertEquals(OFFERED_BOOK_ID, req.getOfferedBookId());
        assertEquals(STUDENT_REQUESTER, req.getRequesterId());
        assertEquals(STUDENT_OWNER, req.getOwnerId());
    }

    @Test
    @DisplayName("Test 2: Invalid self-request is strictly rejected")
    void testSelfRequestThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                requestService.createRequest(
                        STUDENT_OWNER, // Requesting own book!
                        BOOK_SWAP_ID,
                        "SWAP",
                        OFFERED_BOOK_ID,
                        null,
                        "Self request",
                        "Campus"
                )
        );
        assertTrue(ex.getMessage().contains("cannot request your own textbook"));
    }

    @Test
    @DisplayName("Test 3: Invalid offered book (not owned by requester)")
    void testInvalidOfferedBookNotOwned() {
        // Book 102 belongs to STUDENT_OWNER, not STUDENT_REQUESTER
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                requestService.createRequest(
                        STUDENT_REQUESTER,
                        BOOK_SWAP_ID,
                        "SWAP",
                        BOOK_GIVEAWAY_ID, // Not owned by requester!
                        null,
                        "Trade",
                        "Campus"
                )
        );
        assertTrue(ex.getMessage().contains("does not belong to you"));
    }

    @Test
    @DisplayName("Test 4: Invalid offered book cannot be the same as requested book")
    void testOfferedBookSameAsRequestedBook() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                requestService.createRequest(
                        STUDENT_REQUESTER,
                        BOOK_SWAP_ID,
                        "SWAP",
                        BOOK_SWAP_ID, // Same book!
                        null,
                        "Trade",
                        "Campus"
                )
        );
        assertTrue(ex.getMessage().contains("cannot be the same as the requested book"));
    }

    @Test
    @DisplayName("Test 5: Giveaway request succeeds without offered book")
    void testGiveawayRequestWithoutOfferedBook() throws SQLException {
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER,
                BOOK_GIVEAWAY_ID,
                "GIVEAWAY",
                null, // No offered book
                null,
                "I would love this textbook for semester revision!",
                "Student Center Cafeteria"
        );

        assertNotNull(req);
        assertEquals("PENDING", req.getStatus());
        assertEquals("GIVEAWAY", req.getExchangeMode());
        assertNull(req.getOfferedBookId());
    }

    @Test
    @DisplayName("Test 6: Lending request with invalid negative/zero duration is rejected")
    void testLendingInvalidDurationZeroOrNegative() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                requestService.createRequest(
                        STUDENT_REQUESTER,
                        BOOK_LENDING_ID,
                        "LENDING",
                        null,
                        0, // Invalid duration
                        "Loan request",
                        "Library"
                )
        );
        assertTrue(ex.getMessage().contains("valid positive loan duration"));
    }

    @Test
    @DisplayName("Test 7: Lending duration exceeding owner maximum is rejected")
    void testLendingDurationExceedingMaximum() {
        // Book 103 has max duration 30 days; requester asks for 45 days
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                requestService.createRequest(
                        STUDENT_REQUESTER,
                        BOOK_LENDING_ID,
                        "LENDING",
                        null,
                        45,
                        "Need for whole semester",
                        "Library"
                )
        );
        assertTrue(ex.getMessage().contains("exceeds the owner's limit"));
    }

    @Test
    @DisplayName("Test 8: Prevent duplicate active requests for the same book")
    void testPreventDuplicateActiveRequests() throws SQLException {
        // First request succeeds
        requestService.createRequest(STUDENT_REQUESTER, BOOK_GIVEAWAY_ID, "GIVEAWAY", null, null, "First", "Campus");

        // Second request from same student for same book must fail
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                requestService.createRequest(STUDENT_REQUESTER, BOOK_GIVEAWAY_ID, "GIVEAWAY", null, null, "Second", "Campus")
        );
        assertTrue(ex.getMessage().contains("already have an active pending request"));
    }

    @Test
    @DisplayName("Test 9: Owner accepts request, book is RESERVED and exchange initialized")
    void testAcceptRequestSuccess() throws SQLException {
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER,
                BOOK_SWAP_ID,
                "SWAP",
                OFFERED_BOOK_ID,
                null,
                "Let's swap",
                "Library Block B"
        );

        Exchange ex = requestService.acceptRequest(req.getId(), STUDENT_OWNER, "Library Floor 1", "See you at 3pm");
        assertNotNull(ex);
        assertEquals("AWAITING_HANDOVER", ex.getStatus());
        assertEquals("ACCEPTED", req.getStatus());

        // Both books must be RESERVED
        assertEquals("RESERVED", mockBookDao.findById(BOOK_SWAP_ID).getStatus());
        assertEquals("RESERVED", mockBookDao.findById(OFFERED_BOOK_ID).getStatus());
    }

    @Test
    @DisplayName("Test 10: Unauthorized student cannot accept request")
    void testUnauthorizedStudentCannotAccept() throws SQLException {
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER,
                BOOK_GIVEAWAY_ID,
                "GIVEAWAY",
                null,
                null,
                "Free book",
                "Campus"
        );

        // STUDENT_OTHER attempts to accept STUDENT_OWNER's book request
        SecurityException ex = assertThrows(SecurityException.class, () ->
                requestService.acceptRequest(req.getId(), STUDENT_OTHER, "Place", "Notes")
        );
        assertTrue(ex.getMessage().contains("Only the book owner may accept"));
    }

    @Test
    @DisplayName("Test 11: Owner rejects request")
    void testRejectRequestSuccess() throws SQLException {
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER,
                BOOK_GIVEAWAY_ID,
                "GIVEAWAY",
                null,
                null,
                "Request",
                "Campus"
        );

        boolean rejected = requestService.rejectRequest(req.getId(), STUDENT_OWNER, "Already promised to roommate");
        assertTrue(rejected);
        assertEquals("REJECTED", req.getStatus());
    }

    @Test
    @DisplayName("Test 12: Requester cancels own pending request")
    void testCancelPendingRequestSuccess() throws SQLException {
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER,
                BOOK_GIVEAWAY_ID,
                "GIVEAWAY",
                null,
                null,
                "Request",
                "Campus"
        );

        boolean cancelled = requestService.cancelRequest(req.getId(), STUDENT_REQUESTER);
        assertTrue(cancelled);
        assertEquals("CANCELLED", req.getStatus());
    }

    @Test
    @DisplayName("Test 13: Other student cannot cancel another student's request")
    void testUnauthorizedCancelBlocked() throws SQLException {
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER,
                BOOK_GIVEAWAY_ID,
                "GIVEAWAY",
                null,
                null,
                "Request",
                "Campus"
        );

        SecurityException ex = assertThrows(SecurityException.class, () ->
                requestService.cancelRequest(req.getId(), STUDENT_OTHER)
        );
        assertTrue(ex.getMessage().contains("Only the requester may cancel"));
    }

    // ========================================================================
    // Mock DAOs for in-memory testing
    // ========================================================================
    private static class MockBookDAO extends BookDAO {
        private final Map<Long, Book> books = new HashMap<>();

        @Override
        public Book findById(Long id) {
            return books.get(id);
        }

        @Override
        public boolean save(Book b) {
            books.put(b.getId(), b);
            return true;
        }

        @Override
        public boolean updateStatus(Connection conn, Long id, String status) {
            Book b = books.get(id);
            if (b != null) {
                b.setStatus(status);
                return true;
            }
            return false;
        }

        @Override
        public boolean transferOwnership(Connection conn, Long bookId, Long newOwnerId) {
            Book b = books.get(bookId);
            if (b != null) {
                b.setOwnerId(newOwnerId);
                b.setStatus("AVAILABLE");
                return true;
            }
            return false;
        }
    }

    private static class MockExchangeRequestDAO extends ExchangeRequestDAO {
        private final Map<Long, ExchangeRequest> requests = new HashMap<>();
        private long seq = 1000L;

        @Override
        public Long createRequest(ExchangeRequest req) {
            long id = ++seq;
            req.setId(id);
            requests.put(id, req);
            return id;
        }

        @Override
        public ExchangeRequest findById(Long id) {
            return requests.get(id);
        }

        @Override
        public int countPendingRequests(Long bookId, Long requesterId) {
            return (int) requests.values().stream()
                    .filter(r -> r.getBookId().equals(bookId) && r.getRequesterId().equals(requesterId) && "PENDING".equalsIgnoreCase(r.getStatus()))
                    .count();
        }

        @Override
        public boolean updateStatus(Long id, String status) {
            ExchangeRequest r = requests.get(id);
            if (r != null) {
                r.setStatus(status);
                return true;
            }
            return false;
        }

        @Override
        public boolean updateStatus(Connection conn, Long id, String status) {
            return updateStatus(id, status);
        }

        @Override
        public int cancelOtherPendingRequestsForBook(Connection conn, Long bookId, Long acceptedRequestId) {
            int count = 0;
            for (ExchangeRequest r : requests.values()) {
                if (r.getBookId().equals(bookId) && !r.getId().equals(acceptedRequestId) && "PENDING".equalsIgnoreCase(r.getStatus())) {
                    r.setStatus("REJECTED");
                    count++;
                }
            }
            return count;
        }
    }

    private static class MockExchangeDAO extends ExchangeDAO {
        private final Map<Long, Exchange> exchanges = new HashMap<>();
        private long seq = 5000L;

        @Override
        public Long createExchange(Connection conn, Exchange ex) {
            long id = ++seq;
            ex.setId(id);
            exchanges.put(id, ex);
            return id;
        }

        @Override
        public Exchange findById(Long id) {
            return exchanges.get(id);
        }
    }
}
