package com.bookexchange.service;

import com.bookexchange.dao.BookDAO;
import com.bookexchange.model.Book;
import com.bookexchange.util.BookCoverResolver;
import com.bookexchange.util.ValidationUtil;

import java.sql.SQLException;
import java.util.List;

/**
 * ============================================================================
 * BookService - Business Logic Layer for Book Management & Discovery
 * ============================================================================
 * Enforces business rules:
 * 1. Title, author, and subject are strictly mandatory.
 * 2. Input lengths are checked to prevent database truncation.
 * 3. Sharing modes (SWAP, GIVEAWAY, LENDING) and condition grades are validated.
 * 4. Lending duration (> 0) is mandatory for LENDING sharing mode.
 * 5. Book ownership is strictly verified in Java before updates and deletions.
 * 6. Deletion of books involved in active accepted exchanges is prohibited.
 * 7. Default status for newly created books is AVAILABLE.
 * ============================================================================
 */
public class BookService {

    private final BookDAO bookDAO;

    public BookService() {
        this.bookDAO = new BookDAO();
    }

    public BookService(BookDAO bookDAO) {
        this.bookDAO = bookDAO;
    }

    /**
     * Creates a new academic book listing for the authenticated student.
     *
     * @param book The book details
     * @param authenticatedOwnerId The verified ID of the logged-in student
     * @return true if created successfully
     * @throws IllegalArgumentException On validation errors
     * @throws SQLException On database errors
     */
    public boolean createBookListing(Book book, Long authenticatedOwnerId) throws SQLException {
        if (authenticatedOwnerId == null || authenticatedOwnerId <= 0) {
            throw new IllegalArgumentException("A valid authenticated student is required to list a book.");
        }

        validateBookFields(book);

        // Bind ownership securely to session student
        book.setOwnerId(authenticatedOwnerId);

        // Initial availability status is always AVAILABLE
        book.setStatus("AVAILABLE");

        return bookDAO.save(book);
    }

    /**
     * Backward-compatible overload for initial seed or test callers.
     */
    public boolean createBookListing(Book book) throws SQLException {
        Long ownerId = book.getOwnerId() != null ? book.getOwnerId() : 1L;
        return createBookListing(book, ownerId);
    }

    /**
     * Retrieves book details by primary key.
     */
    public Book getBookById(Long id) throws SQLException {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid book ID: " + id);
        }
        return bookDAO.findById(id);
    }

    /**
     * Retrieves all available books, with optional exclusion of student's own books.
     */
    public List<Book> getAllAvailableBooks(Long excludeOwnerId) throws SQLException {
        return bookDAO.findAllAvailable(excludeOwnerId);
    }

    public List<Book> getAllAvailableBooks() throws SQLException {
        return getAllAvailableBooks(null);
    }

    /**
     * Retrieves all books listed by the current student (for My Books).
     */
    public List<Book> getMyBooks(Long ownerId, String statusFilter) throws SQLException {
        if (ownerId == null || ownerId <= 0) {
            throw new IllegalArgumentException("Owner ID is required to fetch student books.");
        }
        return bookDAO.findByOwnerId(ownerId, statusFilter);
    }

    /**
     * Multi-criteria search and filter across available books.
     */
    public List<Book> searchAndFilterBooks(String query, String subject, String condition, String mode, Long excludeOwnerId)
            throws SQLException {
        return bookDAO.searchAndFilter(query, subject, condition, mode, excludeOwnerId);
    }

    public List<Book> getBooksBySubject(String subject) throws SQLException {
        return searchAndFilterBooks(null, subject, null, null, null);
    }

    public List<Book> getBooksByMode(String mode) throws SQLException {
        return searchAndFilterBooks(null, null, null, mode, null);
    }

    /**
     * Updates an existing book listing with backend ownership validation.
     *
     * @param book The updated book details
     * @param authenticatedOwnerId The authenticated student's ID
     * @return true if update succeeded
     * @throws SecurityException If student does not own the listing
     * @throws IllegalArgumentException On validation errors
     * @throws SQLException On database errors
     */
    public boolean updateBookListing(Book book, Long authenticatedOwnerId) throws SQLException {
        if (book.getId() == null || book.getId() <= 0) {
            throw new IllegalArgumentException("Book ID is required for updating listing.");
        }
        if (authenticatedOwnerId == null || authenticatedOwnerId <= 0) {
            throw new IllegalArgumentException("Authentication required to update book listing.");
        }

        // Verify book existence and ownership
        Book existing = bookDAO.findById(book.getId());
        if (existing == null) {
            throw new IllegalArgumentException("Book listing not found with ID: " + book.getId());
        }

        if (!existing.getOwnerId().equals(authenticatedOwnerId)) {
            throw new SecurityException("Unauthorized: You can only edit books listed by your own account.");
        }

        validateBookFields(book);

        // Preserve owner ID
        book.setOwnerId(authenticatedOwnerId);

        // If status was not provided in update, retain existing
        if (!ValidationUtil.isNotEmpty(book.getStatus())) {
            book.setStatus(existing.getStatus());
        }

        return bookDAO.update(book, authenticatedOwnerId);
    }

    /**
     * Deletes a book listing with ownership validation and active exchange check.
     *
     * @param bookId The book to delete
     * @param authenticatedOwnerId The student requesting deletion
     * @return true if deleted
     * @throws SecurityException If user is not the owner
     * @throws IllegalStateException If book is involved in an active exchange
     * @throws IllegalArgumentException If book not found
     * @throws SQLException On database error
     */
    public boolean deleteBookListing(Long bookId, Long authenticatedOwnerId) throws SQLException {
        if (bookId == null || bookId <= 0) {
            throw new IllegalArgumentException("Book ID is required for deletion.");
        }
        if (authenticatedOwnerId == null || authenticatedOwnerId <= 0) {
            throw new IllegalArgumentException("Authentication required to delete a book listing.");
        }

        Book existing = bookDAO.findById(bookId);
        if (existing == null) {
            throw new IllegalArgumentException("Book listing not found with ID: " + bookId);
        }

        if (!existing.getOwnerId().equals(authenticatedOwnerId)) {
            throw new SecurityException("Unauthorized: You can only delete books listed by your own account.");
        }

        // Integrity check: Prevent deletion of books involved in active exchanges
        if (bookDAO.isBookInActiveExchange(bookId)) {
            throw new IllegalStateException("Cannot delete book: This textbook is currently involved in an active or accepted exchange.");
        }

        return bookDAO.delete(bookId, authenticatedOwnerId);
    }

    /**
     * Validates common book fields.
     */
    private void validateBookFields(Book book) {
        if (book == null) {
            throw new IllegalArgumentException("Book data cannot be null.");
        }
        if (!ValidationUtil.isNotEmpty(book.getTitle())) {
            throw new IllegalArgumentException("Book title is required.");
        }
        if (book.getTitle().trim().length() > 255) {
            throw new IllegalArgumentException("Book title cannot exceed 255 characters.");
        }
        if (!ValidationUtil.isNotEmpty(book.getAuthor())) {
            throw new IllegalArgumentException("Author name is required.");
        }
        if (book.getAuthor().trim().length() > 255) {
            throw new IllegalArgumentException("Author name cannot exceed 255 characters.");
        }
        if (!ValidationUtil.isNotEmpty(book.getSubject())) {
            throw new IllegalArgumentException("Academic subject area is required.");
        }
        if (book.getSubject().trim().length() > 128) {
            throw new IllegalArgumentException("Subject area cannot exceed 128 characters.");
        }

        // Auto-resolve cover image using canonical BookCoverResolver
        String resolvedCover = BookCoverResolver.resolveCoverUrl(
            book.getTitle(), book.getAuthor(), book.getIsbn(), book.getImageUrl()
        );
        book.setImageUrl(resolvedCover);

        // Validate condition grade
        if (!ValidationUtil.isNotEmpty(book.getConditionGrade())) {
            book.setConditionGrade("GOOD");
        } else {
            String cond = book.getConditionGrade().trim().toUpperCase();
            if (!cond.equals("LIKE_NEW") && !cond.equals("GOOD") && !cond.equals("FAIR") && !cond.equals("NEW")) {
                throw new IllegalArgumentException("Invalid condition grade: " + book.getConditionGrade() + ". Allowed: LIKE_NEW, GOOD, FAIR.");
            }
            book.setConditionGrade(cond);
        }

        // Validate sharing mode
        if (!ValidationUtil.isValidSharingMode(book.getSharingMode())) {
            throw new IllegalArgumentException("Invalid sharing mode: " + book.getSharingMode() + ". Allowed: SWAP, GIVEAWAY, LENDING.");
        }
        book.setSharingMode(book.getSharingMode().trim().toUpperCase());

        // Validate lending mode rules
        if ("LENDING".equals(book.getSharingMode())) {
            if (book.getLendingMaxDurationDays() == null || book.getLendingMaxDurationDays() <= 0) {
                throw new IllegalArgumentException("Lending mode requires a maximum duration greater than 0 days (e.g. 15, 30, 60 days).");
            }
            if (book.getLendingMaxDurationDays() > 180) {
                throw new IllegalArgumentException("Lending duration cannot exceed 180 days (one academic semester).");
            }
        }
    }
}
