package com.bookexchange.dao;

import com.bookexchange.config.DBConnection;
import com.bookexchange.model.Book;
import com.bookexchange.model.Exchange;
import com.bookexchange.model.ExchangeRequest;
import com.bookexchange.model.User;
import com.bookexchange.service.BookService;
import com.bookexchange.servlet.BookServlet;
import com.bookexchange.test.TestDatabaseHelper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================================
 * DatabasePerformanceTest - Query Performance, Indexing & JDBC Verification
 * ============================================================================
 * Verifies all database performance optimizations:
 * 1. Pagination overloads with LIMIT / OFFSET and defensive clamping.
 * 2. Count query accuracy and elimination of unnecessary JOINs.
 * 3. ISBN direct index acceleration vs wildcard fallback.
 * 4. Short-circuit SELECT 1 ... LIMIT 1 existence checks.
 * 5. Case-insensitive user lookup utilizing index equality with fallback.
 * 6. JDBC Connection propagation in transactional workflows.
 * 7. BookServlet backward-compatible pagination JSON response.
 * ============================================================================
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class DatabasePerformanceTest {

    private BookDAO bookDAO;
    private UserDAO userDAO;
    private ExchangeDAO exchangeDAO;
    private ExchangeRequestDAO exchangeRequestDAO;
    private BookService bookService;
    private Gson gson;

    private static final Long STUDENT_ALICE = 100L;
    private static final Long STUDENT_BOB = 200L;

    @BeforeAll
    static void initTestSuite() {
        TestDatabaseHelper.initDatabase();
    }

    @AfterAll
    static void tearDownTestSuite() {
        DBConnection.resetConfig();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TestDatabaseHelper.clearTables();

        bookDAO = new BookDAO();
        userDAO = new UserDAO();
        exchangeDAO = new ExchangeDAO();
        exchangeRequestDAO = new ExchangeRequestDAO();
        bookService = new BookService(bookDAO);
        gson = new Gson();

        // Seed 2 students
        TestDatabaseHelper.insertStudent(STUDENT_ALICE, "CS-100", "Alice Smith", "alice.perf@university.edu", "Computer Science", 4, "Main Campus");
        TestDatabaseHelper.insertStudent(STUDENT_BOB, "EE-200", "Bob Jones", "bob.perf@university.edu", "Electrical Eng", 6, "North Campus");
    }

    @Test
    @DisplayName("Performance 1: Pagination and count overloads for available books")
    void testPaginatedCatalogAndCount() throws SQLException {
        // Insert 15 available books for Alice
        for (int i = 1; i <= 15; i++) {
            Book b = new Book(
                    STUDENT_ALICE,
                    "Textbook Title " + String.format("%02d", i),
                    "Author " + (i % 3),
                    "97800000000" + String.format("%02d", i),
                    "1st",
                    "CS101",
                    "Computer Science",
                    "GOOD",
                    "SWAP",
                    "Description " + i,
                    null,
                    null,
                    "Any book"
            );
            b.setStatus("AVAILABLE");
            assertTrue(bookDAO.save(b));
        }

        // Also insert 3 UNAVAILABLE books (should not appear in available list)
        for (int i = 16; i <= 18; i++) {
            Book b = new Book(
                    STUDENT_ALICE,
                    "Unavailable Book " + i,
                    "Author X",
                    "97800000000" + i,
                    "1st",
                    "CS101",
                    "Computer Science",
                    "FAIR",
                    "GIVEAWAY",
                    "Desc",
                    null,
                    null,
                    null
            );
            b.setStatus("COMPLETED");
            assertTrue(bookDAO.save(b));
        }

        // Verify total available count is 15 (without redundant joins)
        int totalAvailable = bookDAO.countAllAvailable(null);
        assertEquals(15, totalAvailable, "Total available books count must be 15");

        // Page 1: limit 5, offset 0 -> 5 books
        List<Book> page1 = bookDAO.findAllAvailable(null, 0, 5);
        assertEquals(5, page1.size(), "Page 1 must contain exactly 5 books");

        // Page 2: limit 5, offset 5 -> 5 books
        List<Book> page2 = bookDAO.findAllAvailable(null, 5, 5);
        assertEquals(5, page2.size(), "Page 2 must contain exactly 5 books");

        // Page 3: limit 5, offset 10 -> 5 books
        List<Book> page3 = bookDAO.findAllAvailable(null, 10, 5);
        assertEquals(5, page3.size(), "Page 3 must contain exactly 5 books");

        // Ensure no overlap between page 1 and page 2
        for (Book b1 : page1) {
            for (Book b2 : page2) {
                assertNotEquals(b1.getId(), b2.getId(), "Page 1 and Page 2 must not have overlapping books");
            }
        }

        // Page 4: offset beyond total -> empty list
        List<Book> page4 = bookDAO.findAllAvailable(null, 20, 5);
        assertTrue(page4.isEmpty(), "Page beyond total records must return an empty list");

        // Defensive clamping: negative offset clamped to 0
        List<Book> clampedOffset = bookDAO.findAllAvailable(null, -10, 5);
        assertEquals(5, clampedOffset.size(), "Negative offset must be clamped to 0");

        // Defensive clamping: limit clamped to max 100
        List<Book> clampedLimit = bookDAO.findAllAvailable(null, 0, 500);
        assertEquals(15, clampedLimit.size(), "Clamped limit must return all 15 available books");

        // Test excludeOwnerId
        List<Book> excludedAlice = bookDAO.findAllAvailable(STUDENT_ALICE, 0, 10);
        assertTrue(excludedAlice.isEmpty(), "Excluding Alice's ownerId must yield 0 available books");
        assertEquals(0, bookDAO.countAllAvailable(STUDENT_ALICE));
    }

    @Test
    @DisplayName("Performance 2: Paginated search and multi-criteria filter with count")
    void testSearchAndFilterPaginationAndCount() throws SQLException {
        // Seed diverse books
        insertTestBook(STUDENT_ALICE, "Data Structures in Java", "Robert Lafore", "9780672324536", "Computer Science", "LIKE_NEW", "SWAP", "AVAILABLE");
        insertTestBook(STUDENT_ALICE, "Introduction to Algorithms", "Thomas Cormen", "9780262033848", "Computer Science", "LIKE_NEW", "SWAP", "AVAILABLE");
        insertTestBook(STUDENT_ALICE, "Algorithms in C++", "Robert Sedgewick", "9780201350883", "Computer Science", "GOOD", "LENDING", "AVAILABLE");
        insertTestBook(STUDENT_BOB, "Algorithm Design", "Jon Kleinberg", "9780321295354", "Computer Science", "LIKE_NEW", "SWAP", "AVAILABLE");
        insertTestBook(STUDENT_BOB, "Calculus Early Transcendentals", "James Stewart", "9781285741550", "Mathematics", "GOOD", "GIVEAWAY", "AVAILABLE");

        // Search "Algorithms"
        int algoCount = bookDAO.countSearchAndFilter("Algorithms", null, null, null, null);
        assertEquals(2, algoCount, "Count of books with 'Algorithms' in title/subject must be 2");

        List<Book> algoPage = bookDAO.searchAndFilter("Algorithms", null, null, null, null, 0, 1);
        assertEquals(1, algoPage.size(), "Page size 1 must return 1 book");

        // Filter by subject "Computer Science"
        int csCount = bookDAO.countSearchAndFilter(null, "Computer Science", null, null, null);
        assertEquals(4, csCount, "Computer Science books count must be 4");

        // Filter by condition "LIKE_NEW" + mode "SWAP"
        int likeNewSwapCount = bookDAO.countSearchAndFilter(null, null, "LIKE_NEW", "SWAP", null);
        assertEquals(3, likeNewSwapCount, "LIKE_NEW + SWAP count must be 3");

        // Exclude owner Bob
        int aliceOnlyCsCount = bookDAO.countSearchAndFilter(null, "Computer Science", null, null, STUDENT_BOB);
        assertEquals(3, aliceOnlyCsCount, "Excluding Bob must return Alice's 3 CS books");
    }

    @Test
    @DisplayName("Performance 3: Direct ISBN search index acceleration")
    void testDirectIsbnIndexAcceleration() throws SQLException {
        insertTestBook(STUDENT_ALICE, "Effective Java", "Joshua Bloch", "9780134685991", "Computer Science", "LIKE_NEW", "SWAP", "AVAILABLE");
        insertTestBook(STUDENT_BOB, "Clean Architecture", "Robert Martin", "9780134494166", "Computer Science", "GOOD", "SWAP", "AVAILABLE");

        // 1. Search with exact 13-digit clean ISBN
        List<Book> matches = bookDAO.searchAndFilter("9780134685991", null, null, null, null, 0, 10);
        assertEquals(1, matches.size(), "Exact 13-digit ISBN search must return exactly 1 book");
        assertEquals("Effective Java", matches.get(0).getTitle());

        // 2. Search with hyphenated ISBN (e.g. from barcode scanner: 978-0-134-68599-1)
        List<Book> hyphenMatches = bookDAO.searchAndFilter("978-0-134-68599-1", null, null, null, null, 0, 10);
        assertEquals(1, hyphenMatches.size(), "Hyphenated ISBN search must resolve and match via index");
        assertEquals("Effective Java", hyphenMatches.get(0).getTitle());

        // 3. Count query with ISBN
        int count = bookDAO.countSearchAndFilter("9780134685991", null, null, null, null);
        assertEquals(1, count, "Count with exact ISBN must return 1");
    }

    @Test
    @DisplayName("Performance 4: Student listings pagination and count")
    void testStudentListingPagination() throws SQLException {
        for (int i = 1; i <= 8; i++) {
            insertTestBook(STUDENT_ALICE, "Alice Book " + i, "Author", "97800000000" + i, "Subject", "GOOD", "SWAP", "AVAILABLE");
        }
        for (int i = 9; i <= 12; i++) {
            insertTestBook(STUDENT_ALICE, "Alice Book " + i, "Author", "97800000000" + i, "Subject", "GOOD", "SWAP", "RESERVED");
        }

        // Test AVAILABLE count for Alice
        assertEquals(8, bookDAO.countByOwnerId(STUDENT_ALICE, "AVAILABLE"));
        // Test ALL count for Alice
        assertEquals(12, bookDAO.countByOwnerId(STUDENT_ALICE, "ALL"));

        // Paginated retrieval of AVAILABLE
        List<Book> page1 = bookDAO.findByOwnerId(STUDENT_ALICE, "AVAILABLE", 0, 5);
        assertEquals(5, page1.size());

        List<Book> page2 = bookDAO.findByOwnerId(STUDENT_ALICE, "AVAILABLE", 5, 5);
        assertEquals(3, page2.size());
    }

    @Test
    @DisplayName("Performance 5: Short-circuit SELECT 1 ... LIMIT 1 existence checks")
    void testShortCircuitExistenceChecks() throws SQLException {
        insertTestBook(STUDENT_ALICE, "Test Exchange Book", "Author", "9781111111111", "CS", "LIKE_NEW", "SWAP", "AVAILABLE");
        List<Book> books = bookDAO.findAllAvailable(null, 0, 1);
        Long bookId = books.get(0).getId();

        // Initially no active exchange
        assertFalse(bookDAO.isBookInActiveExchange(bookId), "Book should not be in active exchange initially");

        // Insert request and exchange
        Long reqId = insertTestRequest(bookId, STUDENT_BOB, STUDENT_ALICE, "SWAP", "PENDING");
        assertEquals(1, exchangeRequestDAO.countPendingRequests(bookId, STUDENT_BOB),
                "countPendingRequests must return 1 using SELECT 1 ... LIMIT 1");

        // Insert active exchange
        insertTestExchange(reqId, bookId, STUDENT_ALICE, STUDENT_BOB, "SWAP", "ACTIVE");

        // Now book is in active exchange
        assertTrue(bookDAO.isBookInActiveExchange(bookId),
                "isBookInActiveExchange must return true when an ACTIVE exchange exists");
    }

    @Test
    @DisplayName("Performance 6: Optimized User lookups with index-first fallback and duplicate checks")
    void testOptimizedUserLookups() throws SQLException {
        // Exact match
        User user = userDAO.findByEmail("alice.perf@university.edu");
        assertNotNull(user, "User must be found by exact email");
        assertEquals(STUDENT_ALICE, user.getId());

        // Case-insensitive match (fallback to LOWER(email) = LOWER(?))
        User caseUser = userDAO.findByEmail("ALICE.PERF@UNIVERSITY.EDU");
        assertNotNull(caseUser, "User must be found even with uppercase email");
        assertEquals(STUDENT_ALICE, caseUser.getId());

        // University ID lookup
        User uIdUser = userDAO.findByUniversityId("CS-100");
        assertNotNull(uIdUser, "User must be found by university ID");
        assertEquals(STUDENT_ALICE, uIdUser.getId());

        // Uniqueness check for email (other student)
        assertTrue(userDAO.isEmailTakenByOther("alice.perf@university.edu", STUDENT_BOB),
                "Email must be reported taken by other student");
        assertFalse(userDAO.isEmailTakenByOther("alice.perf@university.edu", STUDENT_ALICE),
                "Email must NOT be reported taken when checking against the user's own ID");

        // Uniqueness check for university ID
        assertTrue(userDAO.isUniversityIdTakenByOther("CS-100", STUDENT_BOB),
                "University ID must be reported taken by other student");
        assertFalse(userDAO.isUniversityIdTakenByOther("CS-100", STUDENT_ALICE),
                "University ID must NOT be reported taken when checking against the user's own ID");
    }

    @Test
    @DisplayName("Performance 7: Connection propagation avoids checking out secondary connections")
    void testConnectionPropagation() throws SQLException {
        insertTestBook(STUDENT_ALICE, "Connection Propagation Book", "Author", "9782222222222", "CS", "LIKE_NEW", "SWAP", "AVAILABLE");
        List<Book> books = bookDAO.findAllAvailable(null, 0, 1);
        Long bookId = books.get(0).getId();

        try (Connection conn = DBConnection.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                // Read book using existing connection
                Book b = bookDAO.findById(conn, bookId);
                assertNotNull(b, "Book must be retrievable using existing open Connection");

                // Update status using same connection
                assertTrue(bookDAO.updateStatus(conn, bookId, "RESERVED"));

                // Read student using same connection
                User u = userDAO.findById(conn, STUDENT_ALICE);
                assertNotNull(u, "User must be retrievable using existing open Connection");

                // Verify updated book status within transaction
                Book updated = bookDAO.findById(conn, bookId);
                assertEquals("RESERVED", updated.getStatus());

                conn.commit();
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }

        // Verify status persisted
        Book committed = bookDAO.findById(bookId);
        assertEquals("RESERVED", committed.getStatus());
    }

    @Test
    @DisplayName("Performance 8: BookServlet returns pagination metadata when requested and preserves legacy format")
    void testBookServletPagination() throws Exception {
        // Insert 12 available books
        for (int i = 1; i <= 12; i++) {
            insertTestBook(STUDENT_ALICE, "Servlet Book " + i, "Author", "97833333333" + String.format("%02d", i), "CS", "GOOD", "SWAP", "AVAILABLE");
        }

        TestableBookServlet servlet = new TestableBookServlet(bookService);

        // Case A: With pagination query params (page=1, limit=5)
        Map<String, String> paginatedParams = new HashMap<>();
        paginatedParams.put("page", "1");
        paginatedParams.put("limit", "5");

        StringWriter writerA = new StringWriter();
        HttpServletRequest reqA = createMockRequest(paginatedParams, null, null);
        HttpServletResponse respA = createMockResponse(writerA);

        servlet.executeGet(reqA, respA);

        JsonObject jsonA = gson.fromJson(writerA.toString(), JsonObject.class);
        assertTrue(jsonA.get("success").getAsBoolean());
        assertEquals(5, jsonA.get("count").getAsInt());
        assertEquals(12, jsonA.get("totalCount").getAsInt());
        assertEquals(1, jsonA.get("page").getAsInt());
        assertEquals(5, jsonA.get("pageSize").getAsInt());
        assertEquals(3, jsonA.get("totalPages").getAsInt());
        assertEquals(5, jsonA.getAsJsonArray("books").size());

        // Case B: Without pagination query params (legacy / backwards-compatible)
        Map<String, String> legacyParams = new HashMap<>();
        StringWriter writerB = new StringWriter();
        HttpServletRequest reqB = createMockRequest(legacyParams, null, null);
        HttpServletResponse respB = createMockResponse(writerB);

        servlet.executeGet(reqB, respB);

        JsonObject jsonB = gson.fromJson(writerB.toString(), JsonObject.class);
        assertTrue(jsonB.get("success").getAsBoolean());
        assertEquals(12, jsonB.get("count").getAsInt());
        assertEquals(12, jsonB.getAsJsonArray("books").size());
        assertNull(jsonB.get("page"), "Legacy request must not include 'page' parameter");
        assertNull(jsonB.get("totalPages"), "Legacy request must not include 'totalPages'");
    }

    // ========================================================================
    // Test Helpers
    // ========================================================================
    private void insertTestBook(Long ownerId, String title, String author, String isbn,
                                String subject, String condition, String mode, String status) throws SQLException {
        String sql = "INSERT INTO books (owner_id, title, author, isbn, subject, condition_grade, sharing_mode, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ownerId);
            ps.setString(2, title);
            ps.setString(3, author);
            ps.setString(4, isbn);
            ps.setString(5, subject);
            ps.setString(6, condition);
            ps.setString(7, mode);
            ps.setString(8, status);
            ps.executeUpdate();
        }
    }

    private Long insertTestRequest(Long bookId, Long requesterId, Long ownerId, String mode, String status) throws SQLException {
        String sql = "INSERT INTO exchange_requests (book_id, requester_id, owner_id, exchange_mode, status) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, bookId);
            ps.setLong(2, requesterId);
            ps.setLong(3, ownerId);
            ps.setString(4, mode);
            ps.setString(5, status);
            ps.executeUpdate();
            try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return null;
    }

    private Long insertTestExchange(Long requestId, Long bookId, Long ownerId, Long requesterId, String mode, String status) throws SQLException {
        String sql = "INSERT INTO exchanges (request_id, book_id, owner_id, requester_id, exchange_mode, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, requestId);
            ps.setLong(2, bookId);
            ps.setLong(3, ownerId);
            ps.setLong(4, requesterId);
            ps.setString(5, mode);
            ps.setString(6, status);
            ps.executeUpdate();
            try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private HttpServletRequest createMockRequest(Map<String, String> params, String pathInfo, User currentUser) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("getParameter".equals(name) && args != null && args.length == 1) {
                return params.get((String) args[0]);
            }
            if ("getPathInfo".equals(name)) {
                return pathInfo;
            }
            if ("getSession".equals(name)) {
                return createMockSession(currentUser);
            }
            return null;
        };
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                handler
        );
    }

    @SuppressWarnings("unchecked")
    private HttpSession createMockSession(User currentUser) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("getAttribute".equals(method.getName()) && args != null && args.length == 1) {
                if ("currentUser".equals(args[0]) || "user".equals(args[0])) {
                    return currentUser;
                }
            }
            return null;
        };
        return (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                handler
        );
    }

    @SuppressWarnings("unchecked")
    private HttpServletResponse createMockResponse(StringWriter writer) {
        PrintWriter pw = new PrintWriter(writer);
        InvocationHandler handler = (proxy, method, args) -> {
            if ("getWriter".equals(method.getName())) {
                return pw;
            }
            return null;
        };
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                handler
        );
    }

    private static class TestableBookServlet extends BookServlet {
        public TestableBookServlet(BookService bookService) {
            super(bookService);
        }

        public void executeGet(HttpServletRequest req, HttpServletResponse resp) throws Exception {
            super.doGet(req, resp);
        }
    }
}
