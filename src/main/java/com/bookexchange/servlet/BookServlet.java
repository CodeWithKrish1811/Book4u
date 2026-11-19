package com.bookexchange.servlet;

import com.bookexchange.model.Book;
import com.bookexchange.service.BookService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/**
 * ============================================================================
 * BookServlet - REST-Style Servlet for Academic Book Operations
 * ============================================================================
 * Endpoint: /api/books
 * Handles:
 * - GET: List available books, with optional ?subject=... or ?mode=... filters
 * - POST: Create a new academic book listing
 * ============================================================================
 */
@WebServlet(name = "BookServlet", urlPatterns = "/api/books")
public class BookServlet extends HttpServlet {

    private final BookService bookService = new BookService();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String subject = req.getParameter("subject");
        String mode = req.getParameter("mode");
        String idParam = req.getParameter("id");

        try (PrintWriter out = resp.getWriter()) {
            if (idParam != null && !idParam.isBlank()) {
                Long id = Long.parseLong(idParam);
                Book book = bookService.getBookById(id);
                if (book != null) {
                    out.print(gson.toJson(book));
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Book not found with ID: " + id);
                    out.print(gson.toJson(error));
                }
                return;
            }

            List<Book> books;
            if (subject != null && !subject.isBlank()) {
                books = bookService.getBooksBySubject(subject);
            } else if (mode != null && !mode.isBlank()) {
                books = bookService.getBooksByMode(mode);
            } else {
                books = bookService.getAllAvailableBooks();
            }

            out.print(gson.toJson(books));
            out.flush();

        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid numeric ID format: " + idParam);
            resp.getWriter().print(gson.toJson(error));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            resp.getWriter().print(gson.toJson(error));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // Read JSON payload from request body
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        try (PrintWriter out = resp.getWriter()) {
            Book newBook = gson.fromJson(sb.toString(), Book.class);

            if (newBook == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = new JsonObject();
                err.addProperty("error", "Empty or malformed JSON payload");
                out.print(gson.toJson(err));
                return;
            }

            // For initial demonstration, fallback owner ID if not provided
            if (newBook.getOwnerId() == null) {
                newBook.setOwnerId(1L);
            }

            boolean saved = bookService.createBookListing(newBook);
            if (saved) {
                resp.setStatus(HttpServletResponse.SC_CREATED);
                JsonObject success = new JsonObject();
                success.addProperty("success", true);
                success.addProperty("message", "Book listing created successfully!");
                success.addProperty("bookId", newBook.getId());
                out.print(gson.toJson(success));
            } else {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject err = new JsonObject();
                err.addProperty("error", "Failed to insert book listing.");
                out.print(gson.toJson(err));
            }

        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject err = new JsonObject();
            err.addProperty("error", e.getMessage());
            resp.getWriter().print(gson.toJson(err));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject err = new JsonObject();
            err.addProperty("error", "Database operation failed: " + e.getMessage());
            resp.getWriter().print(gson.toJson(err));
        }
    }
}
