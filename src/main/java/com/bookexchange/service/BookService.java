package com.bookexchange.service;

import com.bookexchange.dao.BookDAO;
import com.bookexchange.model.Book;
import com.bookexchange.util.ValidationUtil;

import java.sql.SQLException;
import java.util.List;

/**
 * ============================================================================
 * BookService - Business Logic Layer
 * ============================================================================
 * Enforces business rules for book listings across SWAP, GIVEAWAY, and LENDING modes.
 * Enforces:
 * - Title and author mandatory
 * - Valid sharing mode
 * - If LENDING mode, duration must be specified (> 0)
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

    public List<Book> getAllAvailableBooks() throws SQLException {
        return bookDAO.findAllAvailable();
    }

    public Book getBookById(Long id) throws SQLException {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid book ID: " + id);
        }
        return bookDAO.findById(id);
    }

    public List<Book> getBooksBySubject(String subject) throws SQLException {
        if (!ValidationUtil.isNotEmpty(subject)) {
            return getAllAvailableBooks();
        }
        return bookDAO.findBySubject(subject.trim());
    }

    public List<Book> getBooksByMode(String mode) throws SQLException {
        if (!ValidationUtil.isValidSharingMode(mode)) {
            return getAllAvailableBooks();
        }
        return bookDAO.findBySharingMode(mode.trim().toUpperCase());
    }

    public boolean createBookListing(Book book) throws SQLException {
        if (!ValidationUtil.isNotEmpty(book.getTitle())) {
            throw new IllegalArgumentException("Book title cannot be blank.");
        }
        if (!ValidationUtil.isNotEmpty(book.getAuthor())) {
            throw new IllegalArgumentException("Book author cannot be blank.");
        }
        if (!ValidationUtil.isValidSharingMode(book.getSharingMode())) {
            throw new IllegalArgumentException("Invalid sharing mode. Allowed: SWAP, GIVEAWAY, LENDING.");
        }

        // Validate lending mode rules
        if ("LENDING".equalsIgnoreCase(book.getSharingMode())) {
            if (book.getLendingMaxDurationDays() == null || book.getLendingMaxDurationDays() <= 0) {
                throw new IllegalArgumentException("Lending mode requires a maximum duration greater than 0 days.");
            }
        }

        // Set default status to AVAILABLE
        book.setStatus("AVAILABLE");

        return bookDAO.save(book);
    }
}
