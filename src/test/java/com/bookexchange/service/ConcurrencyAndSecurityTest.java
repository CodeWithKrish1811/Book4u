package com.bookexchange.service;

import com.bookexchange.config.DBConnection;
import com.bookexchange.dao.BookDAO;
import com.bookexchange.dao.ExchangeDAO;
import com.bookexchange.dao.ExchangeRequestDAO;
import com.bookexchange.model.Book;
import com.bookexchange.model.Exchange;
import com.bookexchange.model.ExchangeRequest;
import com.bookexchange.test.TestDatabaseHelper;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================================
 * ConcurrencyAndSecurityTest - Real Database Integration & QA Hardening Suite
 * ============================================================================
 * ARCHITECTURAL INTEGRITY:
 * 1. Invokes ACTUAL PRODUCTION SERVICE CLASSES (ExchangeRequestService, ExchangeService).
 *    Zero production service methods are overridden or mocked.
 * 2. Operates against a dedicated, isolated MySQL-compatible test database.
 *    Completely isolated from production Railway database.
 * 3. Tests real multi-threaded JDBC concurrency with separate connections and row locking.
 * 4. Tests real JDBC transaction rollbacks on simulated hardware/constraint failures.
 * 5. Tests complete lifecycle for all 3 sharing modes: SWAP, GIVEAWAY, LENDING.
 * 6. Tests IDOR and security controls enforced directly by production methods.
 * ============================================================================
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class ConcurrencyAndSecurityTest {

    private ExchangeRequestService requestService;
    private ExchangeService exchangeService;
    private BookDAO bookDAO;
    private ExchangeRequestDAO requestDAO;
    private ExchangeDAO exchangeDAO;

    private static final Long STUDENT_OWNER = 10L;
    private static final Long STUDENT_REQUESTER_1 = 21L;
    private static final Long STUDENT_REQUESTER_2 = 22L;
    private static final Long STUDENT_ATTACKER = 99L;

    private static final Long BOOK_AVAILABLE_ID = 501L;
    private static final Long BOOK_OFFERED_BY_R1 = 601L;
    private static final Long BOOK_OFFERED_BY_R2 = 602L;
    private static final Long BOOK_GIVEAWAY_ID = 502L;
    private static final Long BOOK_LENDING_ID = 503L;

    @BeforeAll
    static void initSuite() {
        TestDatabaseHelper.initDatabase();
    }

    @AfterAll
    static void tearDownSuite() {
        DBConnection.resetConfig();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TestDatabaseHelper.clearTables();

        // 1. Instantiate REAL production DAOs
        bookDAO = new BookDAO();
        requestDAO = new ExchangeRequestDAO();
        exchangeDAO = new ExchangeDAO();

        // 2. Instantiate REAL production Services - NO OVERRIDES
        requestService = new ExchangeRequestService(requestDAO, exchangeDAO, bookDAO);
        exchangeService = new ExchangeService(exchangeDAO, bookDAO, requestDAO);

        // 3. Seed Students
        TestDatabaseHelper.insertStudent(STUDENT_OWNER, "U2023CS010", "Aarav Owner", "aarav@campus.edu", "Computer Science", 5, "North Campus");
        TestDatabaseHelper.insertStudent(STUDENT_REQUESTER_1, "U2023CS021", "Priya Req1", "priya@campus.edu", "Computer Science", 5, "North Campus");
        TestDatabaseHelper.insertStudent(STUDENT_REQUESTER_2, "U2023EC022", "Rohan Req2", "rohan@campus.edu", "Electronics", 4, "South Campus");
        TestDatabaseHelper.insertStudent(STUDENT_ATTACKER, "U2024XX099", "Eve Attacker", "attacker@campus.edu", "Management", 2, "East Campus");

        // 4. Seed Books
        TestDatabaseHelper.insertBook(BOOK_AVAILABLE_ID, STUDENT_OWNER, "Computer Networks", "Tanenbaum", "SWAP", "AVAILABLE", null);
        TestDatabaseHelper.insertBook(BOOK_OFFERED_BY_R1, STUDENT_REQUESTER_1, "Data Mining", "Han", "SWAP", "AVAILABLE", null);
        TestDatabaseHelper.insertBook(BOOK_OFFERED_BY_R2, STUDENT_REQUESTER_2, "Web Development", "Duckett", "SWAP", "AVAILABLE", null);
        TestDatabaseHelper.insertBook(BOOK_GIVEAWAY_ID, STUDENT_OWNER, "Intro to Engineering", "Holtzapple", "GIVEAWAY", "AVAILABLE", null);
        TestDatabaseHelper.insertBook(BOOK_LENDING_ID, STUDENT_OWNER, "Database Concepts", "Silberschatz", "LENDING", "AVAILABLE", 30);
    }

    // ========================================================================
    // SECTION 1: REAL MULTI-THREADED CONCURRENCY TESTING (Actual DB & Locks)
    // ========================================================================

    @Test
    @DisplayName("Concurrency 1: Two competing requests cannot both be accepted; only one succeeds with DB locking")
    void testConcurrentAcceptanceRaceCondition() throws Exception {
        // Requester 1 creates SWAP request
        ExchangeRequest req1 = requestService.createRequest(
                STUDENT_REQUESTER_1, BOOK_AVAILABLE_ID, "SWAP", BOOK_OFFERED_BY_R1, null, "Request 1", "Library Floor 1"
        );
        // Requester 2 creates competing SWAP request for the same book
        ExchangeRequest req2 = requestService.createRequest(
                STUDENT_REQUESTER_2, BOOK_AVAILABLE_ID, "SWAP", BOOK_OFFERED_BY_R2, null, "Request 2", "Library Floor 2"
        );

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);

        List<Exchange> successes = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        // Thread 1 attempts to accept req1
        executor.submit(() -> {
            try {
                startGate.await();
                Exchange ex = requestService.acceptRequest(req1.getId(), STUDENT_OWNER, "Library Floor 1", "Notes 1");
                successes.add(ex);
            } catch (Throwable t) {
                failures.add(t);
            } finally {
                endGate.countDown();
            }
        });

        // Thread 2 attempts to accept req2
        executor.submit(() -> {
            try {
                startGate.await();
                Exchange ex = requestService.acceptRequest(req2.getId(), STUDENT_OWNER, "Library Floor 2", "Notes 2");
                successes.add(ex);
            } catch (Throwable t) {
                failures.add(t);
            } finally {
                endGate.countDown();
            }
        });

        // Fire both threads simultaneously
        startGate.countDown();
        boolean completed = endGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrent acceptance tasks timed out");

        // RIGOROUS CONCURRENCY INVARIANTS:
        // 1. Exactly 1 acceptance succeeded
        assertEquals(1, successes.size(), "Exactly one competing acceptance must succeed");
        // 2. Exactly 1 acceptance failed with expected concurrency exception
        assertEquals(1, failures.size(), "Competing acceptance must fail with concurrency exception");
        assertTrue(failures.get(0) instanceof IllegalStateException || failures.get(0) instanceof SQLException,
                "Failure must be IllegalStateException or database concurrency lock/deadlock SQLException: " + failures.get(0).getMessage());

        // 3. Database verification
        Book requestedBook = bookDAO.findById(BOOK_AVAILABLE_ID);
        assertEquals("RESERVED", requestedBook.getStatus(), "Requested book must be RESERVED by the winner");

        ExchangeRequest r1DB = requestDAO.findById(req1.getId());
        ExchangeRequest r2DB = requestDAO.findById(req2.getId());

        if (successes.get(0).getRequestId().equals(req1.getId())) {
            assertEquals("ACCEPTED", r1DB.getStatus());
            assertEquals("REJECTED", r2DB.getStatus(), "Losing request must be auto-rejected");
            assertEquals("RESERVED", bookDAO.findById(BOOK_OFFERED_BY_R1).getStatus());
            assertEquals("AVAILABLE", bookDAO.findById(BOOK_OFFERED_BY_R2).getStatus(), "Losing offered book must remain AVAILABLE");
        } else {
            assertEquals("ACCEPTED", r2DB.getStatus());
            assertEquals("REJECTED", r1DB.getStatus(), "Losing request must be auto-rejected");
            assertEquals("RESERVED", bookDAO.findById(BOOK_OFFERED_BY_R2).getStatus());
            assertEquals("AVAILABLE", bookDAO.findById(BOOK_OFFERED_BY_R1).getStatus(), "Losing offered book must remain AVAILABLE");
        }
    }

    @Test
    @DisplayName("Concurrency 2: Simultaneous duplicate requests by the same student are prevented")
    void testConcurrentDuplicateRequestCreation() throws Exception {
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    ExchangeRequest req = requestService.createRequest(
                            STUDENT_REQUESTER_1, BOOK_AVAILABLE_ID, "SWAP", BOOK_OFFERED_BY_R1, null, "Simultaneous " + idx, "Library"
                    );
                    if (req != null) {
                        successCount.incrementAndGet();
                    }
                } catch (IllegalStateException e) {
                    if (e.getMessage().contains("already have an active pending request")) {
                        duplicateCount.incrementAndGet();
                    }
                } catch (Throwable ignored) {
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startGate.countDown();
        boolean completed = endGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Duplicate request race condition tasks timed out");

        // At most 1 request can succeed; the rest must be rejected by duplicate check
        assertEquals(1, successCount.get(), "At most one pending request per student must succeed");
        assertEquals(threads - 1, duplicateCount.get(), "Competing attempts must be rejected with duplicate pending notice");

        int dbPending = requestDAO.countPendingRequests(BOOK_AVAILABLE_ID, STUDENT_REQUESTER_1);
        assertEquals(1, dbPending, "Real DB must contain exactly 1 pending request");
    }

    @Test
    @DisplayName("Concurrency 3: SWAP cannot accept if offered book was already reserved elsewhere")
    void testSwapAcceptanceFailsIfOfferedBookAlreadyReserved() throws SQLException {
        // Requester 1 submits request offering book 601
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER_1, BOOK_AVAILABLE_ID, "SWAP", BOOK_OFFERED_BY_R1, null, "Swap", "Library"
        );

        // Offered book gets reserved by another transaction prior to acceptance
        try (Connection conn = DBConnection.getConnection()) {
            bookDAO.updateStatus(conn, BOOK_OFFERED_BY_R1, "RESERVED");
        }

        // Owner attempts to accept req
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                requestService.acceptRequest(req.getId(), STUDENT_OWNER, "Library", "Notes")
        );
        assertTrue(ex.getMessage().contains("offered textbook is no longer available"));

        // Requested book MUST remain AVAILABLE
        assertEquals("AVAILABLE", bookDAO.findById(BOOK_AVAILABLE_ID).getStatus());
        // Request must remain PENDING
        assertEquals("PENDING", requestDAO.findById(req.getId()).getStatus());
    }

    // ========================================================================
    // SECTION 2: REAL TRANSACTION ROLLBACK TESTING (Actual JDBC conn.rollback())
    // ========================================================================

    @Test
    @DisplayName("Transaction 1: Failed exchange insertion rolls back book reservation and request acceptance")
    void testTransactionRollbackOnFailedExchangeInsertion() throws SQLException {
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER_1, BOOK_AVAILABLE_ID, "SWAP", BOOK_OFFERED_BY_R1, null, "Swap", "Library"
        );

        // Add a temporary CHECK constraint on the real exchanges table that fails on a marker keyword
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE exchanges ADD CONSTRAINT chk_test_fail CHECK (handover_notes NOT LIKE '%FAIL_TRIGGER%')");
        }

        try {
            // Attempt acceptance with the failing marker notes
            SQLException sqlEx = assertThrows(SQLException.class, () ->
                    requestService.acceptRequest(req.getId(), STUDENT_OWNER, "Library", "FAIL_TRIGGER_TEST")
            );
            assertNotNull(sqlEx.getMessage());

            // VERIFY TRANSACTION ROLLBACK:
            // 1. Requested book status was rolled back to AVAILABLE (not left RESERVED)
            Book requestedBook = bookDAO.findById(BOOK_AVAILABLE_ID);
            assertEquals("AVAILABLE", requestedBook.getStatus(), "Requested book must rollback to AVAILABLE");

            // 2. Offered book status was rolled back to AVAILABLE (not left RESERVED)
            Book offeredBook = bookDAO.findById(BOOK_OFFERED_BY_R1);
            assertEquals("AVAILABLE", offeredBook.getStatus(), "Offered book must rollback to AVAILABLE");

            // 3. Request status was rolled back to PENDING (not left ACCEPTED)
            ExchangeRequest reqInDb = requestDAO.findById(req.getId());
            assertEquals("PENDING", reqInDb.getStatus(), "Exchange request must rollback to PENDING");

            // 4. Zero exchange records created in DB
            try (Connection conn = DBConnection.getConnection();
                 Statement stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT COUNT(*) FROM exchanges")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "No exchange record should exist after transaction rollback");
            }

        } finally {
            // Cleanup constraint
            try (Connection conn = DBConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE exchanges DROP CONSTRAINT chk_test_fail");
            } catch (SQLException ignored) {
            }
        }
    }

    @Test
    @DisplayName("Transaction 2: Failed SWAP ownership transfer rolls back both book updates and exchange status")
    void testTransactionRollbackOnSwapHandoverFailure() throws SQLException {
        // Setup accepted SWAP exchange
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER_1, BOOK_AVAILABLE_ID, "SWAP", BOOK_OFFERED_BY_R1, null, "Swap", "Library"
        );
        Exchange ex = requestService.acceptRequest(req.getId(), STUDENT_OWNER, "Library", "Valid notes");
        assertNotNull(ex);

        // Owner confirms handover first (single confirmation)
        exchangeService.confirmHandover(ex.getId(), STUDENT_OWNER);
        Exchange midState = exchangeDAO.findById(ex.getId());
        assertTrue(midState.isOwnerHandoverConfirmed());
        assertFalse(midState.isRequesterHandoverConfirmed());
        assertEquals("AWAITING_HANDOVER", midState.getStatus());

        // Add a constraint on books table that prevents book 601 (offered book) from being transferred to owner 10
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE books ADD CONSTRAINT chk_fail_transfer CHECK (id <> " + BOOK_OFFERED_BY_R1 + " OR owner_id <> " + STUDENT_OWNER + ")");
        }

        try {
            // Requester confirms handover (would trigger dual confirmation & ownership transfer)
            // Book 501 transfer succeeds, but Book 601 transfer violates chk_fail_transfer!
            SQLException sqlEx = assertThrows(SQLException.class, () ->
                    exchangeService.confirmHandover(ex.getId(), STUDENT_REQUESTER_1)
            );
            assertNotNull(sqlEx.getMessage());

            // VERIFY TRANSACTION ROLLBACK:
            // 1. Book 501 owner must still be original STUDENT_OWNER (10L), not transferred to Requester (21L)
            Book b1 = bookDAO.findById(BOOK_AVAILABLE_ID);
            assertEquals(STUDENT_OWNER, b1.getOwnerId(), "Book 501 ownership transfer must be rolled back");
            assertEquals("RESERVED", b1.getStatus(), "Book 501 status must remain RESERVED");

            // 2. Book 601 owner must still be STUDENT_REQUESTER_1 (21L)
            Book b2 = bookDAO.findById(BOOK_OFFERED_BY_R1);
            assertEquals(STUDENT_REQUESTER_1, b2.getOwnerId(), "Book 601 ownership must remain with Requester");
            assertEquals("RESERVED", b2.getStatus(), "Book 601 status must remain RESERVED");

            // 3. Exchange status must remain AWAITING_HANDOVER (not COMPLETED)
            Exchange finalEx = exchangeDAO.findById(ex.getId());
            assertEquals("AWAITING_HANDOVER", finalEx.getStatus(), "Exchange status must remain AWAITING_HANDOVER");
            assertFalse(finalEx.isRequesterHandoverConfirmed(), "Requester handover flag update must be rolled back");

        } finally {
            try (Connection conn = DBConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE books DROP CONSTRAINT chk_fail_transfer");
            } catch (SQLException ignored) {
            }
        }
    }

    @Test
    @DisplayName("Transaction 3: Failed return finalization rolls back book and exchange statuses")
    void testTransactionRollbackOnReturnFinalizationFailure() throws SQLException {
        // Setup active LENDING exchange
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER_1, BOOK_LENDING_ID, "LENDING", null, 14, "Lending", "Library"
        );
        Exchange ex = requestService.acceptRequest(req.getId(), STUDENT_OWNER, "Library", "Notes");
        exchangeService.confirmHandover(ex.getId(), STUDENT_OWNER);
        exchangeService.confirmHandover(ex.getId(), STUDENT_REQUESTER_1);

        Exchange activeEx = exchangeDAO.findById(ex.getId());
        assertEquals("ACTIVE", activeEx.getStatus());
        assertEquals("ON_LOAN", bookDAO.findById(BOOK_LENDING_ID).getStatus());

        // Requester confirms return first
        exchangeService.confirmReturn(ex.getId(), STUDENT_REQUESTER_1);

        // Add a constraint that prevents status from becoming RETURNED
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE exchanges ADD CONSTRAINT chk_fail_return CHECK (status <> 'RETURNED')");
        }

        try {
            // Owner confirms return -> triggers finalization -> violates constraint
            SQLException sqlEx = assertThrows(SQLException.class, () ->
                    exchangeService.confirmReturn(ex.getId(), STUDENT_OWNER)
            );
            assertNotNull(sqlEx.getMessage());

            // VERIFY TRANSACTION ROLLBACK:
            // 1. Book must remain ON_LOAN (not set to AVAILABLE)
            Book book = bookDAO.findById(BOOK_LENDING_ID);
            assertEquals("ON_LOAN", book.getStatus(), "Book must remain ON_LOAN after return finalization failure");

            // 2. Exchange must remain ACTIVE
            Exchange finalEx = exchangeDAO.findById(ex.getId());
            assertEquals("ACTIVE", finalEx.getStatus(), "Exchange must remain ACTIVE");
            assertFalse(finalEx.isOwnerReturnConfirmed(), "Owner return confirmation flag must be rolled back");

        } finally {
            try (Connection conn = DBConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE exchanges DROP CONSTRAINT chk_fail_return");
            } catch (SQLException ignored) {
            }
        }
    }

    // ========================================================================
    // SECTION 3: COMPLETE LIFECYCLE TESTS FOR ALL 3 SHARING MODES
    // ========================================================================

    @Test
    @DisplayName("Sharing Mode 1: Complete SWAP lifecycle through production service methods")
    void testCompleteSwapLifecycle() throws SQLException {
        // 1. Submit SWAP request
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER_1, BOOK_AVAILABLE_ID, "SWAP", BOOK_OFFERED_BY_R1, null, "Swap proposal", "Central Library"
        );
        assertNotNull(req.getId());
        assertEquals("PENDING", req.getStatus());

        // 2. Owner accepts request
        Exchange ex = requestService.acceptRequest(req.getId(), STUDENT_OWNER, "Central Library Gate", "Meeting at 2 PM");
        assertNotNull(ex.getId());
        assertEquals("AWAITING_HANDOVER", ex.getStatus());
        assertEquals("ACCEPTED", requestDAO.findById(req.getId()).getStatus());
        assertEquals("RESERVED", bookDAO.findById(BOOK_AVAILABLE_ID).getStatus());
        assertEquals("RESERVED", bookDAO.findById(BOOK_OFFERED_BY_R1).getStatus());

        // 3. Single confirmation does not complete exchange
        Exchange exOwnerDone = exchangeService.confirmHandover(ex.getId(), STUDENT_OWNER);
        assertTrue(exOwnerDone.isOwnerHandoverConfirmed());
        assertFalse(exOwnerDone.isRequesterHandoverConfirmed());
        assertEquals("AWAITING_HANDOVER", exOwnerDone.getStatus());

        // 4. Dual confirmation completes exchange and swaps ownerships
        Exchange exCompleted = exchangeService.confirmHandover(ex.getId(), STUDENT_REQUESTER_1);
        assertTrue(exCompleted.isOwnerHandoverConfirmed());
        assertTrue(exCompleted.isRequesterHandoverConfirmed());
        assertEquals("COMPLETED", exCompleted.getStatus());
        assertNotNull(exCompleted.getStartDate());

        // 5. Verify database ownership transfers
        Book bookA = bookDAO.findById(BOOK_AVAILABLE_ID);
        assertEquals(STUDENT_REQUESTER_1, bookA.getOwnerId(), "Book 501 must be transferred to Requester");
        assertEquals("AVAILABLE", bookA.getStatus(), "Book 501 must be restored to AVAILABLE");

        Book bookB = bookDAO.findById(BOOK_OFFERED_BY_R1);
        assertEquals(STUDENT_OWNER, bookB.getOwnerId(), "Book 601 must be transferred to Owner");
        assertEquals("AVAILABLE", bookB.getStatus(), "Book 601 must be restored to AVAILABLE");
    }

    @Test
    @DisplayName("Sharing Mode 2: Complete GIVEAWAY lifecycle through production service methods")
    void testCompleteGiveawayLifecycle() throws SQLException {
        // 1. Submit GIVEAWAY request (no offered book, no payment)
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER_1, BOOK_GIVEAWAY_ID, "GIVEAWAY", null, null, "Free book request", "Cafeteria"
        );
        assertNotNull(req.getId());
        assertNull(req.getOfferedBookId());
        assertEquals("PENDING", req.getStatus());

        // 2. Owner accepts
        Exchange ex = requestService.acceptRequest(req.getId(), STUDENT_OWNER, "Cafeteria Entrance", "Free to a good student");
        assertEquals("AWAITING_HANDOVER", ex.getStatus());
        assertEquals("RESERVED", bookDAO.findById(BOOK_GIVEAWAY_ID).getStatus());

        // 3. Dual handover confirmation
        exchangeService.confirmHandover(ex.getId(), STUDENT_OWNER);
        Exchange exCompleted = exchangeService.confirmHandover(ex.getId(), STUDENT_REQUESTER_1);
        assertEquals("COMPLETED", exCompleted.getStatus());

        // 4. Recipient is now the owner
        Book book = bookDAO.findById(BOOK_GIVEAWAY_ID);
        assertEquals(STUDENT_REQUESTER_1, book.getOwnerId(), "Recipient must now own the giveaway book");
        assertEquals("AVAILABLE", book.getStatus(), "Giveaway book must be restored to AVAILABLE");
    }

    @Test
    @DisplayName("Sharing Mode 3: Complete LENDING lifecycle with handover and return confirmation")
    void testCompleteLendingLifecycle() throws SQLException {
        // 1. Submit LENDING request with proposed duration
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER_1, BOOK_LENDING_ID, "LENDING", null, 21, "Borrow for exams", "Library Lawn"
        );
        assertNotNull(req.getId());
        assertEquals(21, req.getProposedDurationDays());

        // 2. Owner accepts
        Exchange ex = requestService.acceptRequest(req.getId(), STUDENT_OWNER, "Library Lawn", "Return before finals");
        assertEquals("AWAITING_HANDOVER", ex.getStatus());
        assertEquals("RESERVED", bookDAO.findById(BOOK_LENDING_ID).getStatus());

        // 3. Dual handover confirmation -> loan becomes ACTIVE, book becomes ON_LOAN
        exchangeService.confirmHandover(ex.getId(), STUDENT_OWNER);
        Exchange activeEx = exchangeService.confirmHandover(ex.getId(), STUDENT_REQUESTER_1);
        assertEquals("ACTIVE", activeEx.getStatus());
        assertNotNull(activeEx.getStartDate());
        assertNotNull(activeEx.getDueDate());

        Book loanedBook = bookDAO.findById(BOOK_LENDING_ID);
        assertEquals(STUDENT_OWNER, loanedBook.getOwnerId(), "Owner must retain ownership in LENDING mode");
        assertEquals("ON_LOAN", loanedBook.getStatus(), "Book status must be ON_LOAN");

        // 4. Return confirmation (dual confirmation)
        exchangeService.confirmReturn(ex.getId(), STUDENT_REQUESTER_1);
        Exchange returnedEx = exchangeService.confirmReturn(ex.getId(), STUDENT_OWNER);
        assertEquals("RETURNED", returnedEx.getStatus());
        assertNotNull(returnedEx.getReturnDate());

        Book returnedBook = bookDAO.findById(BOOK_LENDING_ID);
        assertEquals(STUDENT_OWNER, returnedBook.getOwnerId(), "Owner still owns the returned book");
        assertEquals("AVAILABLE", returnedBook.getStatus(), "Book status must be restored to AVAILABLE");
    }

    // ========================================================================
    // SECTION 4: SECURITY & IDOR TESTING (Direct Production Method Validation)
    // ========================================================================

    @Test
    @DisplayName("Security 1: IDOR defense on request view, acceptance, rejection, and cancellation")
    void testIDORDefenseOnRequests() throws SQLException {
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER_1, BOOK_AVAILABLE_ID, "SWAP", BOOK_OFFERED_BY_R1, null, "Swap", "Library"
        );

        // Attacker attempts to view request details
        assertThrows(SecurityException.class, () ->
                requestService.getRequestById(req.getId(), STUDENT_ATTACKER)
        );

        // Attacker attempts to accept request
        assertThrows(SecurityException.class, () ->
                requestService.acceptRequest(req.getId(), STUDENT_ATTACKER, "Spot", "Notes")
        );

        // Attacker attempts to decline/reject request
        assertThrows(SecurityException.class, () ->
                requestService.rejectRequest(req.getId(), STUDENT_ATTACKER, "Decline reason")
        );

        // Attacker attempts to cancel request (only requester may cancel)
        assertThrows(SecurityException.class, () ->
                requestService.cancelRequest(req.getId(), STUDENT_ATTACKER)
        );

        // Owner CANNOT cancel requester's request
        assertThrows(SecurityException.class, () ->
                requestService.cancelRequest(req.getId(), STUDENT_OWNER)
        );

        // Requester CANNOT accept or reject their own request
        assertThrows(SecurityException.class, () ->
                requestService.acceptRequest(req.getId(), STUDENT_REQUESTER_1, "Spot", "Notes")
        );
        assertThrows(SecurityException.class, () ->
                requestService.rejectRequest(req.getId(), STUDENT_REQUESTER_1, "Decline")
        );

        // Requester can legitimately view and cancel
        assertNotNull(requestService.getRequestById(req.getId(), STUDENT_REQUESTER_1));
        assertTrue(requestService.cancelRequest(req.getId(), STUDENT_REQUESTER_1));
        assertEquals("CANCELLED", requestDAO.findById(req.getId()).getStatus());
    }

    @Test
    @DisplayName("Security 2: IDOR defense on physical exchange handovers and loan returns")
    void testIDORDefenseOnExchanges() throws SQLException {
        ExchangeRequest req = requestService.createRequest(
                STUDENT_REQUESTER_1, BOOK_AVAILABLE_ID, "SWAP", BOOK_OFFERED_BY_R1, null, "Swap", "Library"
        );
        Exchange ex = requestService.acceptRequest(req.getId(), STUDENT_OWNER, "Library", "Notes");

        // Attacker attempts to view exchange
        assertThrows(SecurityException.class, () ->
                exchangeService.getExchangeById(ex.getId(), STUDENT_ATTACKER)
        );

        // Attacker attempts to confirm handover
        assertThrows(SecurityException.class, () ->
                exchangeService.confirmHandover(ex.getId(), STUDENT_ATTACKER)
        );

        // Attacker attempts to confirm return
        assertThrows(SecurityException.class, () ->
                exchangeService.confirmReturn(ex.getId(), STUDENT_ATTACKER)
        );
    }

    @Test
    @DisplayName("Security 3: Business rule defense against self-requesting and offering unowned books")
    void testBusinessRuleValidationInProductionServices() {
        // 1. Student requesting their own book is blocked
        IllegalArgumentException selfReqEx = assertThrows(IllegalArgumentException.class, () ->
                requestService.createRequest(STUDENT_OWNER, BOOK_AVAILABLE_ID, "SWAP", BOOK_OFFERED_BY_R1, null, "Self", "Library")
        );
        assertTrue(selfReqEx.getMessage().contains("cannot request your own textbook"));

        // 2. Student offering a book they do not own in SWAP is blocked
        IllegalArgumentException notOwnedEx = assertThrows(IllegalArgumentException.class, () ->
                requestService.createRequest(STUDENT_REQUESTER_1, BOOK_AVAILABLE_ID, "SWAP", BOOK_AVAILABLE_ID, null, "Invalid", "Library")
        );
        assertTrue(notOwnedEx.getMessage().contains("does not belong to you") || notOwnedEx.getMessage().contains("cannot be the same"));

        // 3. Lending with loan duration exceeding owner limit is blocked
        IllegalArgumentException durationEx = assertThrows(IllegalArgumentException.class, () ->
                requestService.createRequest(STUDENT_REQUESTER_1, BOOK_LENDING_ID, "LENDING", null, 60, "Excess duration", "Library")
        );
        assertTrue(durationEx.getMessage().contains("exceeds the owner's limit"));

        // 4. Requesting an unavailable or reserved book is blocked
        assertDoesNotThrow(() -> {
            try (Connection conn = DBConnection.getConnection()) {
                bookDAO.updateStatus(conn, BOOK_AVAILABLE_ID, "RESERVED");
            }
        });
        IllegalStateException unavailEx = assertThrows(IllegalStateException.class, () ->
                requestService.createRequest(STUDENT_REQUESTER_1, BOOK_AVAILABLE_ID, "SWAP", BOOK_OFFERED_BY_R1, null, "Unavail", "Library")
        );
        assertTrue(unavailEx.getMessage().contains("not accepting requests"));
    }
}
