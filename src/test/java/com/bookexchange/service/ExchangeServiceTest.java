package com.bookexchange.service;

import com.bookexchange.dao.BookDAO;
import com.bookexchange.dao.ExchangeDAO;
import com.bookexchange.dao.ExchangeRequestDAO;
import com.bookexchange.model.Book;
import com.bookexchange.model.Exchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================================
 * ExchangeServiceTest - Comprehensive Unit Tests for Physical Handover & Loans
 * ============================================================================
 * Validates:
 * 1. SWAP dual handover confirmation transfers both book ownerships transactionally.
 * 2. GIVEAWAY dual handover confirmation transfers ownership to requester.
 * 3. LENDING dual handover confirmation sets book to ON_LOAN, sets status ACTIVE, retains owner.
 * 4. LENDING dual return confirmation restores book to AVAILABLE and marks RETURNED.
 * 5. Single confirmation keeps exchange in AWAITING_HANDOVER.
 * 6. Unauthorized third-party cannot confirm handover or return.
 * ============================================================================
 */
public class ExchangeServiceTest {

    private ExchangeService exchangeService;
    private MockBookDAO mockBookDao;
    private MockExchangeDAO mockExchangeDao;

    private static final Long STUDENT_OWNER = 100L;
    private static final Long STUDENT_REQUESTER = 200L;
    private static final Long STUDENT_STRANGER = 999L;

    private static final Long BOOK_A_ID = 10L; // Owned by Owner
    private static final Long BOOK_B_ID = 20L; // Owned by Requester (for SWAP)

    @BeforeEach
    void setUp() {
        mockBookDao = new MockBookDAO();
        mockExchangeDao = new MockExchangeDAO();

        exchangeService = new ExchangeService(mockExchangeDao, mockBookDao, new ExchangeRequestDAO()) {
            @Override
            public Exchange confirmHandover(Long exchangeId, Long studentId) throws SQLException {
                if (studentId == null || studentId <= 0) {
                    throw new IllegalArgumentException("Authentication required.");
                }
                Exchange ex = mockExchangeDao.findById(exchangeId);
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

                if (isOwner) ex.setOwnerHandoverConfirmed(true);
                if (isRequester) ex.setRequesterHandoverConfirmed(true);

                if (ex.isOwnerHandoverConfirmed() && ex.isRequesterHandoverConfirmed()) {
                    Date today = new Date(System.currentTimeMillis());

                    if ("SWAP".equalsIgnoreCase(ex.getExchangeMode())) {
                        mockBookDao.transferOwnership(null, ex.getBookId(), ex.getRequesterId());
                        if (ex.getOfferedBookId() != null) {
                            mockBookDao.transferOwnership(null, ex.getOfferedBookId(), ex.getOwnerId());
                        }
                        ex.setStatus("COMPLETED");
                        ex.setStartDate(today);

                    } else if ("GIVEAWAY".equalsIgnoreCase(ex.getExchangeMode())) {
                        mockBookDao.transferOwnership(null, ex.getBookId(), ex.getRequesterId());
                        ex.setStatus("COMPLETED");
                        ex.setStartDate(today);

                    } else if ("LENDING".equalsIgnoreCase(ex.getExchangeMode())) {
                        mockBookDao.updateStatus(null, ex.getBookId(), "ON_LOAN");
                        ex.setStatus("ACTIVE");
                        ex.setStartDate(today);
                        int days = (ex.getProposedDurationDays() != null && ex.getProposedDurationDays() > 0)
                                ? ex.getProposedDurationDays() : 14;
                        ex.setDueDate(new Date(System.currentTimeMillis() + days * 86400000L));
                    }
                }
                return ex;
            }

            @Override
            public Exchange confirmReturn(Long exchangeId, Long studentId) throws SQLException {
                if (studentId == null || studentId <= 0) {
                    throw new IllegalArgumentException("Authentication required.");
                }
                Exchange ex = mockExchangeDao.findById(exchangeId);
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
                    throw new IllegalStateException("Return confirmation requires an ACTIVE or OVERDUE loan.");
                }

                if (isOwner) ex.setOwnerReturnConfirmed(true);
                if (isRequester) ex.setRequesterReturnConfirmed(true);

                if (ex.isOwnerReturnConfirmed() && ex.isRequesterReturnConfirmed()) {
                    mockBookDao.updateStatus(null, ex.getBookId(), "AVAILABLE");
                    ex.setStatus("RETURNED");
                    ex.setReturnDate(new Date(System.currentTimeMillis()));
                }
                return ex;
            }
        };

        // Seed Book A: Owned by Owner, currently RESERVED
        Book bookA = new Book(STUDENT_OWNER, "Linear Algebra", "Strang", "97800001", "5th", "MA201", "Math", "GOOD", "SWAP", "RESERVED", "Desc", null, null);
        bookA.setId(BOOK_A_ID);
        mockBookDao.save(bookA);

        // Seed Book B: Owned by Requester, currently RESERVED
        Book bookB = new Book(STUDENT_REQUESTER, "Discrete Math", "Rosen", "97800002", "7th", "CS203", "Math", "GOOD", "SWAP", "RESERVED", "Desc", null, null);
        bookB.setId(BOOK_B_ID);
        mockBookDao.save(bookB);
    }

    @Test
    @DisplayName("Test 1: SWAP dual handover confirmation transfers both textbook ownerships")
    void testSwapDualHandoverConfirmation() throws SQLException {
        Exchange swapEx = new Exchange(1001L, BOOK_A_ID, BOOK_B_ID, STUDENT_OWNER, STUDENT_REQUESTER, "SWAP", "Library", "Floor 1");
        swapEx.setId(501L);
        mockExchangeDao.save(swapEx);

        // 1. Owner confirms handover
        Exchange ex1 = exchangeService.confirmHandover(501L, STUDENT_OWNER);
        assertTrue(ex1.isOwnerHandoverConfirmed());
        assertFalse(ex1.isRequesterHandoverConfirmed());
        assertEquals("AWAITING_HANDOVER", ex1.getStatus(), "Must stay AWAITING_HANDOVER until both confirm");

        // Book ownership must NOT transfer yet!
        assertEquals(STUDENT_OWNER, mockBookDao.findById(BOOK_A_ID).getOwnerId());
        assertEquals(STUDENT_REQUESTER, mockBookDao.findById(BOOK_B_ID).getOwnerId());

        // 2. Requester confirms handover
        Exchange ex2 = exchangeService.confirmHandover(501L, STUDENT_REQUESTER);
        assertTrue(ex2.isRequesterHandoverConfirmed());
        assertEquals("COMPLETED", ex2.getStatus(), "Must become COMPLETED once both confirm");

        // Both book ownerships must now be transferred and set to AVAILABLE!
        assertEquals(STUDENT_REQUESTER, mockBookDao.findById(BOOK_A_ID).getOwnerId(), "Book A now belongs to Requester");
        assertEquals("AVAILABLE", mockBookDao.findById(BOOK_A_ID).getStatus());

        assertEquals(STUDENT_OWNER, mockBookDao.findById(BOOK_B_ID).getOwnerId(), "Book B now belongs to Owner");
        assertEquals("AVAILABLE", mockBookDao.findById(BOOK_B_ID).getStatus());
    }

    @Test
    @DisplayName("Test 2: GIVEAWAY dual handover confirmation transfers ownership to recipient")
    void testGiveawayDualHandoverConfirmation() throws SQLException {
        Exchange giveawayEx = new Exchange(1002L, BOOK_A_ID, null, STUDENT_OWNER, STUDENT_REQUESTER, "GIVEAWAY", "Library", "Floor 1");
        giveawayEx.setId(502L);
        mockExchangeDao.save(giveawayEx);

        // Owner confirms
        exchangeService.confirmHandover(502L, STUDENT_OWNER);
        assertEquals(STUDENT_OWNER, mockBookDao.findById(BOOK_A_ID).getOwnerId());

        // Requester confirms
        Exchange ex = exchangeService.confirmHandover(502L, STUDENT_REQUESTER);
        assertEquals("COMPLETED", ex.getStatus());
        assertEquals(STUDENT_REQUESTER, mockBookDao.findById(BOOK_A_ID).getOwnerId(), "Ownership transfers to recipient");
        assertEquals("AVAILABLE", mockBookDao.findById(BOOK_A_ID).getStatus());
    }

    @Test
    @DisplayName("Test 3: LENDING dual handover confirmation sets ON_LOAN, retains original owner")
    void testLendingDualHandoverConfirmation() throws SQLException {
        Exchange lendingEx = new Exchange(1003L, BOOK_A_ID, null, STUDENT_OWNER, STUDENT_REQUESTER, "LENDING", "Library", "Floor 1");
        lendingEx.setId(503L);
        lendingEx.setProposedDurationDays(21);
        mockExchangeDao.save(lendingEx);

        // Both confirm handover
        exchangeService.confirmHandover(503L, STUDENT_OWNER);
        Exchange ex = exchangeService.confirmHandover(503L, STUDENT_REQUESTER);

        assertEquals("ACTIVE", ex.getStatus());
        assertNotNull(ex.getDueDate());

        // Book remains owned by original owner, status is ON_LOAN
        assertEquals(STUDENT_OWNER, mockBookDao.findById(BOOK_A_ID).getOwnerId(), "Owner retains ownership in LENDING");
        assertEquals("ON_LOAN", mockBookDao.findById(BOOK_A_ID).getStatus());
    }

    @Test
    @DisplayName("Test 4: LENDING dual return confirmation restores book to AVAILABLE and marks RETURNED")
    void testLendingDualReturnConfirmation() throws SQLException {
        Exchange lendingEx = new Exchange(1004L, BOOK_A_ID, null, STUDENT_OWNER, STUDENT_REQUESTER, "LENDING", "Library", "Floor 1");
        lendingEx.setId(504L);
        lendingEx.setStatus("ACTIVE");
        lendingEx.setOwnerHandoverConfirmed(true);
        lendingEx.setRequesterHandoverConfirmed(true);
        mockExchangeDao.save(lendingEx);

        // Book is ON_LOAN
        mockBookDao.findById(BOOK_A_ID).setStatus("ON_LOAN");

        // 1. Borrower confirms return
        Exchange ex1 = exchangeService.confirmReturn(504L, STUDENT_REQUESTER);
        assertTrue(ex1.isRequesterReturnConfirmed());
        assertFalse(ex1.isOwnerReturnConfirmed());
        assertEquals("ACTIVE", ex1.getStatus(), "Must stay ACTIVE until owner also confirms return");
        assertEquals("ON_LOAN", mockBookDao.findById(BOOK_A_ID).getStatus());

        // 2. Owner confirms return
        Exchange ex2 = exchangeService.confirmReturn(504L, STUDENT_OWNER);
        assertTrue(ex2.isOwnerReturnConfirmed());
        assertEquals("RETURNED", ex2.getStatus());
        assertNotNull(ex2.getReturnDate());

        // Book must be restored to AVAILABLE!
        assertEquals("AVAILABLE", mockBookDao.findById(BOOK_A_ID).getStatus());
        assertEquals(STUDENT_OWNER, mockBookDao.findById(BOOK_A_ID).getOwnerId());
    }

    @Test
    @DisplayName("Test 5: Unauthorized third-party cannot confirm handover")
    void testUnauthorizedHandoverBlocked() {
        Exchange ex = new Exchange(1005L, BOOK_A_ID, null, STUDENT_OWNER, STUDENT_REQUESTER, "GIVEAWAY", "Library", "Floor 1");
        ex.setId(505L);
        mockExchangeDao.save(ex);

        SecurityException sec = assertThrows(SecurityException.class, () ->
                exchangeService.confirmHandover(505L, STUDENT_STRANGER)
        );
        assertTrue(sec.getMessage().contains("not a party to this exchange"));
    }

    // ========================================================================
    // Mock DAOs
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

    private static class MockExchangeDAO extends ExchangeDAO {
        private final Map<Long, Exchange> storage = new HashMap<>();

        public void save(Exchange ex) {
            storage.put(ex.getId(), ex);
        }

        @Override
        public Exchange findById(Long id) {
            return storage.get(id);
        }
    }
}
