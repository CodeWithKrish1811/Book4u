package com.bookexchange.dao;

import com.bookexchange.config.DBConnection;
import com.bookexchange.model.Book;
import com.bookexchange.model.User;
import com.bookexchange.service.BookService;
import com.bookexchange.servlet.BookServlet;
import com.bookexchange.test.TestDatabaseHelper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================================
 * BookDiscoveryAndSearchTest - Book Discovery & Search Verification Suite
 * ============================================================================
 * Verifies all 9 core requirements:
 * 1. Search books by title.
 * 2. Search by author.
 * 3. Search by ISBN.
 * 4. Filter by academic subject.
 * 5. Filter by department or course code (supported by existing schema).
 * 6. Support case-insensitive searching.
 * 7. Handle empty search results gracefully (HTTP 200, empty list, no errors).
 * 8. Prevent SQL injection across all search/filter parameters.
 * 9. Maintain compatibility with existing book fields and UI response format.
 * ============================================================================
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class BookDiscoveryAndSearchTest {

    private BookDAO bookDAO;
    private BookService bookService;
    private Gson gson;

    private static final Long STUDENT_CS = 101L;
    private static final Long STUDENT_EE = 202L;
    private static final Long STUDENT_MECH = 303L;

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
        bookService = new BookService(bookDAO);
        gson = new Gson();

        // Seed 3 students in different departments
        TestDatabaseHelper.insertStudent(STUDENT_CS, "CS-001", "Aarav Sharma", "aarav.cs@campus.edu", "Computer Science", 4, "North Campus");
        TestDatabaseHelper.insertStudent(STUDENT_EE, "EE-002", "Priya Patel", "priya.ee@campus.edu", "Electrical Engineering", 6, "Central Campus");
        TestDatabaseHelper.insertStudent(STUDENT_MECH, "ME-003", "Rohan Verma", "rohan.me@campus.edu", "Mechanical Engineering", 2, "South Campus");

        // Seed comprehensive catalog
        insertBookWithCourse(STUDENT_CS, "Operating System Concepts", "Abraham Silberschatz", "9781118063330", "CS204", "Operating Systems", "LIKE_NEW", "SWAP");
        insertBookWithCourse(STUDENT_CS, "Database System Concepts", "Abraham Silberschatz", "9780078022159", "CS304", "DBMS", "VERY_GOOD", "LENDING");
        insertBookWithCourse(STUDENT_CS, "Effective Java", "Joshua Bloch", "9780134685991", "CS301", "Java", "LIKE_NEW", "SWAP");
        insertBookWithCourse(STUDENT_EE, "Computer Networks", "Sarika Gupta", "9789386173263", "EC501", "Networks", "GOOD", "SWAP");
        insertBookWithCourse(STUDENT_EE, "Signals and Systems", "Alan V. Oppenheim", "9780138147570", "EE301", "Electrical", "GOOD", "LENDING");
        insertBookWithCourse(STUDENT_MECH, "Engineering Thermodynamics", "P.K. Nag", "9789352606429", "ME201", "Thermodynamics", "ACCEPTABLE", "GIVEAWAY");
    }

    @Test
    @DisplayName("Requirement 1: Search books specifically by title (case-insensitive substring)")
    void testSearchByTitle() throws SQLException {
        // Exact match
        List<Book> exact = bookService.searchBooksByTitle("Operating System Concepts", null);
        assertEquals(1, exact.size());
        assertEquals("Operating System Concepts", exact.get(0).getTitle());

        // Case-insensitive substring match
        List<Book> lower = bookService.searchBooksByTitle("operating system", null);
        assertEquals(1, lower.size());

        List<Book> upper = bookService.searchBooksByTitle("OPERATING", null);
        assertEquals(1, upper.size());

        // Multi-match substring
        List<Book> concepts = bookService.searchBooksByTitle("Concepts", null);
        assertEquals(2, concepts.size(), "Should match both Operating System Concepts and Database System Concepts");
    }

    @Test
    @DisplayName("Requirement 2: Search books specifically by author (case-insensitive substring)")
    void testSearchByAuthor() throws SQLException {
        // Multi-book author
        List<Book> silberschatz = bookService.searchBooksByAuthor("Silberschatz", null);
        assertEquals(2, silberschatz.size(), "Silberschatz authored 2 books in the catalog");

        // Case-insensitive author search
        List<Book> lower = bookService.searchBooksByAuthor("silberschatz", null);
        assertEquals(2, lower.size());

        List<Book> bloch = bookService.searchBooksByAuthor("joshua bloch", null);
        assertEquals(1, bloch.size());
        assertEquals("Effective Java", bloch.get(0).getTitle());
    }

    @Test
    @DisplayName("Requirement 3: Search books specifically by ISBN (exact, hyphenated, index-accelerated)")
    void testSearchByIsbn() throws SQLException {
        // Clean 13-digit ISBN
        List<Book> matchClean = bookService.searchBooksByIsbn("9780134685991", null);
        assertEquals(1, matchClean.size());
        assertEquals("Effective Java", matchClean.get(0).getTitle());

        // Hyphenated barcode ISBN
        List<Book> matchHyphen = bookService.searchBooksByIsbn("978-0-134-68599-1", null);
        assertEquals(1, matchHyphen.size(), "Hyphenated ISBN should match the clean ISBN in database");
        assertEquals("Effective Java", matchHyphen.get(0).getTitle());

        // Substring partial ISBN search
        List<Book> partial = bookService.searchBooksByIsbn("1118063330", null);
        assertEquals(1, partial.size());
        assertEquals("Operating System Concepts", partial.get(0).getTitle());
    }

    @Test
    @DisplayName("Requirement 4: Filter books by academic subject")
    void testFilterBySubject() throws SQLException {
        List<Book> javaBooks = bookService.searchAndFilterBooks(null, null, null, null, "Java", null, null, null, null, null, 0, 0);
        assertEquals(1, javaBooks.size());
        assertEquals("Java", javaBooks.get(0).getSubject());

        // Case-insensitive subject
        List<Book> dbmsBooks = bookService.searchAndFilterBooks(null, null, null, null, "dbms", null, null, null, null, null, 0, 0);
        assertEquals(1, dbmsBooks.size());
        assertEquals("DBMS", dbmsBooks.get(0).getSubject());

        // Non-existent subject
        List<Book> empty = bookService.searchAndFilterBooks(null, null, null, null, "NonExistentSubject", null, null, null, null, null, 0, 0);
        assertTrue(empty.isEmpty());
    }

    @Test
    @DisplayName("Requirement 5: Filter by course code and department (existing schema)")
    void testFilterByCourseAndDepartment() throws SQLException {
        // Filter by course code
        List<Book> cs204 = bookService.filterBooksByCourse("CS204", null);
        assertEquals(1, cs204.size());
        assertEquals("Operating System Concepts", cs204.get(0).getTitle());

        // Case-insensitive course code
        List<Book> lowerCourse = bookService.filterBooksByCourse("cs204", null);
        assertEquals(1, lowerCourse.size());

        // Filter by student owner department
        List<Book> csDept = bookService.filterBooksByDepartment("Computer Science", null);
        assertEquals(3, csDept.size(), "Student CS listed 3 books");

        List<Book> eeDept = bookService.filterBooksByDepartment("Electrical Engineering", null);
        assertEquals(2, eeDept.size(), "Student EE listed 2 books");

        List<Book> mechDept = bookService.filterBooksByDepartment("Mechanical Engineering", null);
        assertEquals(1, mechDept.size(), "Student MECH listed 1 book");

        // Combined filter: Department + Subject
        List<Book> csJava = bookService.searchAndFilterBooks(null, null, null, null, "Java", "Computer Science", null, null, null, null, 0, 0);
        assertEquals(1, csJava.size());
        assertEquals("Effective Java", csJava.get(0).getTitle());
    }

    @Test
    @DisplayName("Requirement 6: Global keyword query searches across title, author, isbn, subject, and course")
    void testGlobalQuerySearch() throws SQLException {
        // Search by title keyword via global query
        List<Book> qTitle = bookService.searchAndFilterBooks("Thermodynamics", null, null, null, null);
        assertEquals(1, qTitle.size());

        // Search by author keyword via global query
        List<Book> qAuthor = bookService.searchAndFilterBooks("Oppenheim", null, null, null, null);
        assertEquals(1, qAuthor.size());

        // Search by course code via global query
        List<Book> qCourse = bookService.searchAndFilterBooks("EC501", null, null, null, null);
        assertEquals(1, qCourse.size());
        assertEquals("Computer Networks", qCourse.get(0).getTitle());

        // Search by ISBN via global query
        List<Book> qIsbn = bookService.searchAndFilterBooks("9780078022159", null, null, null, null);
        assertEquals(1, qIsbn.size());
        assertEquals("Database System Concepts", qIsbn.get(0).getTitle());
    }

    @Test
    @DisplayName("Requirement 7: Empty search results handled gracefully (empty list, count=0, no exceptions)")
    void testEmptySearchResultsGracefully() throws SQLException {
        // Search for gibberish
        List<Book> noMatch = bookService.searchAndFilterBooks("XyZ123NonExistent", null, null, null, null);
        assertNotNull(noMatch, "Result list must not be null");
        assertTrue(noMatch.isEmpty(), "Result list must be empty");

        int count = bookService.countBooks("XyZ123NonExistent", null, null, null, null);
        assertEquals(0, count);

        // Specific field with no match
        List<Book> noAuthor = bookService.searchBooksByAuthor("NonExistentAuthor", null);
        assertTrue(noAuthor.isEmpty());
    }

    @Test
    @DisplayName("Requirement 8: SQL Injection payloads are safely neutralized via PreparedStatement")
    void testSqlInjectionPrevention() throws SQLException {
        // Classic tautology payload
        String sqlInjection1 = "' OR '1'='1";
        List<Book> res1 = bookService.searchAndFilterBooks(sqlInjection1, null, null, null, null);
        assertTrue(res1.isEmpty(), "SQL injection payload must return 0 books and not dump the database");

        // Comment payload
        String sqlInjection2 = "test' OR 1=1 --";
        List<Book> res2 = bookService.searchBooksByTitle(sqlInjection2, null);
        assertTrue(res2.isEmpty());

        // Semicolon drop payload
        String sqlInjection3 = "'; DROP TABLE books; --";
        List<Book> res3 = bookService.searchBooksByAuthor(sqlInjection3, null);
        assertTrue(res3.isEmpty());

        // Verify table still exists and contains all records intact
        int total = bookDAO.countAllAvailable(null);
        assertEquals(6, total, "Database must remain intact after SQL injection attempts");

        // Payload in department filter
        List<Book> resDept = bookService.filterBooksByDepartment("Computer Science' OR '1'='1", null);
        assertTrue(resDept.isEmpty());

        // Payload in course filter
        List<Book> resCourse = bookService.filterBooksByCourse("CS101'; DROP TABLE students; --", null);
        assertTrue(resCourse.isEmpty());
    }

    @Test
    @DisplayName("Requirement 9: BookServlet endpoint integration with title, author, course, and department")
    void testBookServletSearchAndFilterIntegration() throws Exception {
        TestableBookServlet servlet = new TestableBookServlet(bookService);

        // Case 1: Search by author via servlet parameter
        Map<String, String> authorParams = new HashMap<>();
        authorParams.put("author", "Joshua Bloch");

        StringWriter writer1 = new StringWriter();
        HttpServletRequest req1 = createMockRequest(authorParams, null, null);
        HttpServletResponse resp1 = createMockResponse(writer1);

        servlet.executeGet(req1, resp1);

        JsonObject json1 = gson.fromJson(writer1.toString(), JsonObject.class);
        assertTrue(json1.get("success").getAsBoolean());
        assertEquals(1, json1.get("count").getAsInt());
        JsonArray books1 = json1.getAsJsonArray("books");
        assertEquals("Effective Java", books1.get(0).getAsJsonObject().get("title").getAsString());

        // Case 2: Filter by department and course code
        Map<String, String> deptParams = new HashMap<>();
        deptParams.put("department", "Computer Science");
        deptParams.put("courseCode", "CS204");

        StringWriter writer2 = new StringWriter();
        HttpServletRequest req2 = createMockRequest(deptParams, null, null);
        HttpServletResponse resp2 = createMockResponse(writer2);

        servlet.executeGet(req2, resp2);

        JsonObject json2 = gson.fromJson(writer2.toString(), JsonObject.class);
        assertTrue(json2.get("success").getAsBoolean());
        assertEquals(1, json2.get("count").getAsInt());
        assertEquals("Operating System Concepts", json2.getAsJsonArray("books").get(0).getAsJsonObject().get("title").getAsString());

        // Case 3: Empty search via servlet returns success=true and count=0
        Map<String, String> emptyParams = new HashMap<>();
        emptyParams.put("query", "CompletelyAbsentBookTitle999");

        StringWriter writer3 = new StringWriter();
        HttpServletRequest req3 = createMockRequest(emptyParams, null, null);
        HttpServletResponse resp3 = createMockResponse(writer3);

        servlet.executeGet(req3, resp3);

        JsonObject json3 = gson.fromJson(writer3.toString(), JsonObject.class);
        assertTrue(json3.get("success").getAsBoolean());
        assertEquals(0, json3.get("count").getAsInt());
        assertEquals(0, json3.getAsJsonArray("books").size());
    }

    // ========================================================================
    // Helpers
    // ========================================================================
    private void insertBookWithCourse(Long ownerId, String title, String author, String isbn,
                                      String courseCode, String subject, String condition, String mode) throws SQLException {
        String sql = "INSERT INTO books (owner_id, title, author, isbn, course_code, subject, condition_grade, sharing_mode, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'AVAILABLE')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ownerId);
            ps.setString(2, title);
            ps.setString(3, author);
            ps.setString(4, isbn);
            ps.setString(5, courseCode);
            ps.setString(6, subject);
            ps.setString(7, condition);
            ps.setString(8, mode);
            ps.executeUpdate();
        }
    }

    private static class TestableBookServlet extends BookServlet {
        public TestableBookServlet(BookService bookService) {
            super(bookService);
        }

        public void executeGet(HttpServletRequest req, HttpServletResponse resp) throws Exception {
            super.doGet(req, resp);
        }
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
}
