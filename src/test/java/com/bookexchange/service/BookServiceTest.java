package com.bookexchange.service;

import com.bookexchange.dao.BookDAO;
import com.bookexchange.model.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================================
 * BookServiceTest - Comprehensive Scenario Tests for Phase 3
 * ============================================================================
 * Tests the 16 core requirements of Book Management and Discovery:
 * 1. Add a new book.
 * 2. View the added book by ID.
 * 3. Edit book information.
 * 4. Delete a book.
 * 5. Prevent unauthorized deletion (student B cannot delete student A's book).
 * 6. Prevent unauthorized editing (student B cannot edit student A's book).
 * 7. Search by title (case-insensitive).
 * 8. Search by author (case-insensitive).
 * 9. Filter by academic subject.
 * 10. Filter by condition grade (LIKE_NEW, GOOD, FAIR).
 * 11. Filter by sharing mode (SWAP, GIVEAWAY, LENDING).
 * 12. Display an empty search result correctly.
 * 13. Exclude the logged-in student's own books from Explore.
 * 14. Prevent / flag unavailable books.
 * 15. Prevent unauthenticated users from adding books.
 * 16. Prevent deleting books involved in active exchanges.
 * ============================================================================
 */
public class BookServiceTest {

    private BookService bookService;
    private InMemoryBookDAO mockDao;

    private static final Long STUDENT_A_ID = 1L; // Aarav Sharma
    private static final Long STUDENT_B_ID = 2L; // Priya Patel

    @BeforeEach
    void setUp() {
        mockDao = new InMemoryBookDAO();
        bookService = new BookService(mockDao);

        // Seed textbook 1: Owned by Student A
        Book book1 = new Book(
                STUDENT_A_ID,
                "Effective Java",
                "Joshua Bloch",
                "9780134685991",
                "3rd Edition",
                "CS301",
                "Java",
                "LIKE_NEW",
                "SWAP",
                "AVAILABLE",
                "Classic Java guide",
                null,
                "Looking for Computer Networks"
        );
        book1.setId(101L);
        mockDao.saveDirect(book1);

        // Seed textbook 2: Owned by Student B
        Book book2 = new Book(
                STUDENT_B_ID,
                "Database System Concepts",
                "Abraham Silberschatz",
                "9780078022159",
                "7th Edition",
                "CS401",
                "DBMS",
                "GOOD",
                "LENDING",
                "AVAILABLE",
                "Standard DBMS syllabus textbook",
                30,
                null
        );
        book2.setId(102L);
        mockDao.saveDirect(book2);

        // Seed textbook 3: Owned by Student B (Unavailable)
        Book book3 = new Book(
                STUDENT_B_ID,
                "Introduction to Algorithms (CLRS)",
                "Thomas H. Cormen",
                "9780262033848",
                "3rd Edition",
                "CS201",
                "DSA",
                "FAIR",
                "LENDING",
                "ON_LOAN",
                "Currently borrowed for exam",
                45,
                null
        );
        book3.setId(103L);
        mockDao.saveDirect(book3);
    }

    @Test
    @DisplayName("Scenario 1 & 2: Add a new book and view it by ID")
    void testAddAndViewBook() throws SQLException {
        Book newBook = new Book(
                null, // will be assigned by service
                "Computer Networking: A Top-Down Approach",
                "James Kurose, Keith Ross",
                "9780136681557",
                "8th Edition",
                "EC501",
                "Networks",
                "LIKE_NEW",
                "SWAP",
                null,
                "TCP/IP, socket programming and transport protocols",
                null,
                "Looking for Operating Systems"
        );

        boolean created = bookService.createBookListing(newBook, STUDENT_A_ID);
        assertTrue(created, "Book listing should be created successfully");
        assertNotNull(newBook.getId());
        assertEquals("AVAILABLE", newBook.getStatus(), "Initial status must be AVAILABLE");
        assertEquals(STUDENT_A_ID, newBook.getOwnerId(), "Owner ID must be bound to authenticated student");

        // View by ID
        Book retrieved = bookService.getBookById(newBook.getId());
        assertNotNull(retrieved);
        assertEquals("Computer Networking: A Top-Down Approach", retrieved.getTitle());
    }

    @Test
    @DisplayName("Scenario 3: Edit book information by the owner")
    void testEditBookByOwner() throws SQLException {
        Book toEdit = bookService.getBookById(101L);
        assertNotNull(toEdit);

        toEdit.setTitle("Effective Java (Updated 3rd Ed)");
        toEdit.setConditionGrade("GOOD");
        toEdit.setDescription("Updated condition notes with fresh annotations");

        boolean updated = bookService.updateBookListing(toEdit, STUDENT_A_ID);
        assertTrue(updated, "Owner must be permitted to update listing");

        Book updatedBook = bookService.getBookById(101L);
        assertEquals("Effective Java (Updated 3rd Ed)", updatedBook.getTitle());
        assertEquals("GOOD", updatedBook.getConditionGrade());
    }

    @Test
    @DisplayName("Scenario 4: Delete a book by the owner")
    void testDeleteBookByOwner() throws SQLException {
        boolean deleted = bookService.deleteBookListing(101L, STUDENT_A_ID);
        assertTrue(deleted, "Owner must be able to delete their own listing");

        Book check = mockDao.findById(101L);
        assertNull(check, "Book must no longer exist after deletion");
    }

    @Test
    @DisplayName("Scenario 5: Prevent unauthorized deletion by another student")
    void testPreventUnauthorizedDeletion() {
        // Student B tries to delete Student A's book (#101)
        SecurityException ex = assertThrows(SecurityException.class, () ->
                bookService.deleteBookListing(101L, STUDENT_B_ID)
        );
        assertTrue(ex.getMessage().contains("Unauthorized"), "Must reject deletion from non-owner");

        // Verify book was not deleted
        assertNotNull(mockDao.findById(101L));
    }

    @Test
    @DisplayName("Scenario 6: Prevent unauthorized editing by another student")
    void testPreventUnauthorizedEditing() {
        Book toEdit = new Book();
        toEdit.setId(101L);
        toEdit.setTitle("Hacked Title");
        toEdit.setAuthor("Joshua Bloch");
        toEdit.setSubject("Java");
        toEdit.setSharingMode("SWAP");

        // Student B tries to update Student A's book (#101)
        SecurityException ex = assertThrows(SecurityException.class, () ->
                bookService.updateBookListing(toEdit, STUDENT_B_ID)
        );
        assertTrue(ex.getMessage().contains("Unauthorized"), "Must reject update from non-owner");
    }

    @Test
    @DisplayName("Scenario 7: Search by title (case-insensitive)")
    void testSearchByTitle() throws SQLException {
        List<Book> results = bookService.searchAndFilterBooks("effective", null, null, null, null);
        assertEquals(1, results.size());
        assertEquals("Effective Java", results.get(0).getTitle());
    }

    @Test
    @DisplayName("Scenario 8: Search by author (case-insensitive)")
    void testSearchByAuthor() throws SQLException {
        List<Book> results = bookService.searchAndFilterBooks("silberschatz", null, null, null, null);
        assertEquals(1, results.size());
        assertEquals("Database System Concepts", results.get(0).getTitle());
    }

    @Test
    @DisplayName("Scenario 9: Filter by academic subject")
    void testFilterBySubject() throws SQLException {
        List<Book> results = bookService.searchAndFilterBooks(null, "DBMS", null, null, null);
        assertEquals(1, results.size());
        assertEquals("DBMS", results.get(0).getSubject());
    }

    @Test
    @DisplayName("Scenario 10: Filter by condition grade (LIKE_NEW, GOOD, FAIR)")
    void testFilterByCondition() throws SQLException {
        List<Book> results = bookService.searchAndFilterBooks(null, null, "LIKE_NEW", null, null);
        assertEquals(1, results.size());
        assertEquals("LIKE_NEW", results.get(0).getConditionGrade());
    }

    @Test
    @DisplayName("Scenario 11: Filter by sharing mode (SWAP, GIVEAWAY, LENDING)")
    void testFilterBySharingMode() throws SQLException {
        List<Book> results = bookService.searchAndFilterBooks(null, null, null, "LENDING", null);
        // Book 2 is LENDING and AVAILABLE. (Book 3 is LENDING but ON_LOAN, so excluded from available explore catalog)
        assertEquals(1, results.size());
        assertEquals("Database System Concepts", results.get(0).getTitle());
    }

    @Test
    @DisplayName("Scenario 12: Display empty search result correctly")
    void testEmptySearchResult() throws SQLException {
        List<Book> results = bookService.searchAndFilterBooks("QuantumPhysicsNonExistent", null, null, null, null);
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Empty search query must return empty list without throwing");
    }

    @Test
    @DisplayName("Scenario 13: Exclude the logged-in student's own books from Explore")
    void testExcludeOwnBooksFromExplore() throws SQLException {
        // When Student A browses Explore with excludeSelf=true, book #101 (owned by A) must be omitted
        List<Book> results = bookService.searchAndFilterBooks(null, null, null, null, STUDENT_A_ID);
        assertEquals(1, results.size());
        assertEquals("Database System Concepts", results.get(0).getTitle());
        assertNotEquals(STUDENT_A_ID, results.get(0).getOwnerId());
    }

    @Test
    @DisplayName("Scenario 14: Recognize unavailable status on books")
    void testUnavailableBookRecognition() throws SQLException {
        Book book3 = bookService.getBookById(103L);
        assertNotNull(book3);
        assertEquals("ON_LOAN", book3.getStatus());
        assertNotEquals("AVAILABLE", book3.getStatus(), "Borrowed book must not be marked as AVAILABLE");
    }

    @Test
    @DisplayName("Scenario 15: Prevent unauthenticated users from adding books")
    void testPreventUnauthenticatedAdding() {
        Book book = new Book();
        book.setTitle("Compiler Design");
        book.setAuthor("Aho Ullman");
        book.setSubject("Compilers");
        book.setSharingMode("SWAP");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                bookService.createBookListing(book, null)
        );
        assertTrue(ex.getMessage().contains("authenticated student is required"));
    }

    @Test
    @DisplayName("Scenario 16: Prevent deleting books involved in active exchanges")
    void testPreventDeletingBookInActiveExchange() {
        // Mark book 101 as involved in an active exchange
        mockDao.markInActiveExchange(101L, true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                bookService.deleteBookListing(101L, STUDENT_A_ID)
        );
        assertTrue(ex.getMessage().contains("active or accepted exchange"));
    }

    // ========================================================================
    // In-Memory Test DAO Mock to isolate BookService tests
    // ========================================================================
    private static class InMemoryBookDAO extends BookDAO {
        private final Map<Long, Book> storage = new HashMap<>();
        private final Set<Long> booksInActiveExchanges = new HashSet<>();
        private long idSeq = 200L;

        public void saveDirect(Book b) {
            storage.put(b.getId(), b);
        }

        public void markInActiveExchange(Long bookId, boolean inExchange) {
            if (inExchange) {
                booksInActiveExchanges.add(bookId);
            } else {
                booksInActiveExchanges.remove(bookId);
            }
        }

        @Override
        public Book findById(Long id) {
            return storage.get(id);
        }

        @Override
        public boolean save(Book book) {
            long newId = ++idSeq;
            book.setId(newId);
            storage.put(newId, book);
            return true;
        }

        @Override
        public boolean update(Book book, Long ownerId) {
            Book existing = storage.get(book.getId());
            if (existing != null && existing.getOwnerId().equals(ownerId)) {
                storage.put(book.getId(), book);
                return true;
            }
            return false;
        }

        @Override
        public boolean delete(Long bookId, Long ownerId) {
            Book existing = storage.get(bookId);
            if (existing != null && existing.getOwnerId().equals(ownerId)) {
                storage.remove(bookId);
                return true;
            }
            return false;
        }

        @Override
        public boolean isBookInActiveExchange(Long bookId) {
            return booksInActiveExchanges.contains(bookId);
        }

        @Override
        public List<Book> searchAndFilter(String query, String title, String author, String isbn,
                                          String subject, String department, String courseCode,
                                          String condition, String mode, Long excludeOwnerId,
                                          int offset, int limit) throws SQLException {
            return storage.values().stream()
                    .filter(b -> "AVAILABLE".equalsIgnoreCase(b.getStatus()))
                    .filter(b -> excludeOwnerId == null || !b.getOwnerId().equals(excludeOwnerId))
                    .filter(b -> {
                        if (query == null || query.isBlank()) return true;
                        String q = query.toLowerCase();
                        return (b.getTitle() != null && b.getTitle().toLowerCase().contains(q)) ||
                               (b.getAuthor() != null && b.getAuthor().toLowerCase().contains(q)) ||
                               (b.getSubject() != null && b.getSubject().toLowerCase().contains(q)) ||
                               (b.getCourseCode() != null && b.getCourseCode().toLowerCase().contains(q)) ||
                               (b.getIsbn() != null && b.getIsbn().toLowerCase().contains(q));
                    })
                    .filter(b -> title == null || title.isBlank() || (b.getTitle() != null && b.getTitle().toLowerCase().contains(title.trim().toLowerCase())))
                    .filter(b -> author == null || author.isBlank() || (b.getAuthor() != null && b.getAuthor().toLowerCase().contains(author.trim().toLowerCase())))
                    .filter(b -> isbn == null || isbn.isBlank() || (b.getIsbn() != null && b.getIsbn().replace("-", "").toLowerCase().contains(isbn.trim().replace("-", "").toLowerCase())))
                    .filter(b -> subject == null || subject.isBlank() || "ALL".equalsIgnoreCase(subject) || (b.getSubject() != null && b.getSubject().equalsIgnoreCase(subject.trim())))
                    .filter(b -> courseCode == null || courseCode.isBlank() || "ALL".equalsIgnoreCase(courseCode) || (b.getCourseCode() != null && b.getCourseCode().equalsIgnoreCase(courseCode.trim())))
                    .filter(b -> department == null || department.isBlank() || "ALL".equalsIgnoreCase(department) || (b.getOwnerDepartment() != null && b.getOwnerDepartment().toLowerCase().contains(department.trim().toLowerCase())))
                    .filter(b -> condition == null || condition.isBlank() || "ALL".equalsIgnoreCase(condition) || (b.getConditionGrade() != null && b.getConditionGrade().equalsIgnoreCase(condition.trim())))
                    .filter(b -> mode == null || mode.isBlank() || "ALL".equalsIgnoreCase(mode) || (b.getSharingMode() != null && b.getSharingMode().equalsIgnoreCase(mode.trim())))
                    .skip(Math.max(0, offset))
                    .limit(limit > 0 ? limit : Long.MAX_VALUE)
                    .collect(Collectors.toList());
        }

        @Override
        public int countSearchAndFilter(String query, String title, String author, String isbn,
                                        String subject, String department, String courseCode,
                                        String condition, String mode, Long excludeOwnerId) throws SQLException {
            return searchAndFilter(query, title, author, isbn, subject, department, courseCode, condition, mode, excludeOwnerId, 0, 0).size();
        }

        @Override
        public List<Book> searchAndFilter(String query, String subject, String condition, String mode, Long excludeOwnerId) throws SQLException {
            return searchAndFilter(query, null, null, null, subject, null, null, condition, mode, excludeOwnerId, 0, 0);
        }

        @Override
        public List<Book> searchAndFilter(String query, String subject, String condition, String mode, Long excludeOwnerId, int offset, int limit) throws SQLException {
            return searchAndFilter(query, null, null, null, subject, null, null, condition, mode, excludeOwnerId, offset, limit);
        }

        @Override
        public int countSearchAndFilter(String query, String subject, String condition, String mode, Long excludeOwnerId) throws SQLException {
            return countSearchAndFilter(query, null, null, null, subject, null, null, condition, mode, excludeOwnerId);
        }

        @Override
        public List<Book> findByOwnerId(Long ownerId, String statusFilter, int offset, int limit) throws SQLException {
            List<Book> all = findByOwnerId(ownerId, statusFilter);
            return all.stream().skip(Math.max(0, offset)).limit(Math.max(1, limit)).collect(Collectors.toList());
        }

        @Override
        public int countByOwnerId(Long ownerId, String statusFilter) throws SQLException {
            return findByOwnerId(ownerId, statusFilter).size();
        }

        @Override
        public List<Book> findAllAvailable(Long excludeOwnerId, int offset, int limit) throws SQLException {
            return searchAndFilter(null, null, null, null, null, null, null, null, null, excludeOwnerId, offset, limit);
        }

        @Override
        public int countAllAvailable(Long excludeOwnerId) throws SQLException {
            return countSearchAndFilter(null, null, null, null, null, null, null, null, null, excludeOwnerId);
        }
    }
}
