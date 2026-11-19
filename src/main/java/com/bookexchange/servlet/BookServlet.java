package com.bookexchange.servlet;

import com.bookexchange.model.Book;
import com.bookexchange.model.User;
import com.bookexchange.service.BookService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/**
 * ============================================================================
 * BookServlet - REST Controller for Academic Book Management & Discovery
 * ============================================================================
 * Endpoints:
 * - GET    /api/books                 : List available books / search & filter
 * - GET    /api/books?id=...          : View single book details
 * - GET    /api/books?myBooks=true    : View authenticated student's listings
 * - POST   /api/books                 : Add a new academic book listing
 * - PUT    /api/books                 : Update an existing book listing
 * - POST   /api/books/update          : Update book listing (POST alias)
 * - DELETE /api/books?id=...          : Delete a book listing
 * - POST   /api/books/delete          : Delete book listing (POST alias)
 * ============================================================================
 */
@WebServlet(name = "BookServlet", urlPatterns = {"/api/books", "/api/books/*"})
public class BookServlet extends HttpServlet {

    private final BookService bookService = new BookService();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String idParam = req.getParameter("id");
        String myBooksParam = req.getParameter("myBooks");
        String query = req.getParameter("query");
        String subject = req.getParameter("subject");
        String condition = req.getParameter("condition");
        String mode = req.getParameter("mode");
        String status = req.getParameter("status");
        String excludeSelfParam = req.getParameter("excludeSelf");

        User currentUser = getAuthenticatedUser(req);

        try (PrintWriter out = resp.getWriter()) {

            // 1. Single Book Details by ID
            if (idParam != null && !idParam.isBlank()) {
                Long id = Long.parseLong(idParam);
                Book book = bookService.getBookById(id);

                if (book != null) {
                    JsonObject res = new JsonObject();
                    res.addProperty("success", true);
                    res.add("book", gson.toJsonTree(book));

                    // Add permission flags for frontend convenience
                    boolean isOwner = currentUser != null && currentUser.getId().equals(book.getOwnerId());
                    res.addProperty("isOwner", isOwner);
                    res.addProperty("canRequest", !isOwner && "AVAILABLE".equalsIgnoreCase(book.getStatus()));

                    out.print(gson.toJson(res));
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    out.print(gson.toJson(createErrorJson("Book not found with ID: " + id)));
                }
                return;
            }

            // 2. My Books (Student's own listings)
            if ("true".equalsIgnoreCase(myBooksParam)) {
                if (currentUser == null) {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    out.print(gson.toJson(createErrorJson("Authentication required to view your listings.")));
                    return;
                }

                List<Book> myBooks = bookService.getMyBooks(currentUser.getId(), status);
                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.add("books", gson.toJsonTree(myBooks));
                res.addProperty("count", myBooks.size());
                out.print(gson.toJson(res));
                return;
            }

            // 3. Explore & Discovery Catalog (Search & Multi-Filter)
            Long excludeOwnerId = null;
            if ("true".equalsIgnoreCase(excludeSelfParam) && currentUser != null) {
                excludeOwnerId = currentUser.getId();
            }

            List<Book> books = bookService.searchAndFilterBooks(query, subject, condition, mode, excludeOwnerId);

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.add("books", gson.toJsonTree(books));
            res.addProperty("count", books.size());
            out.print(gson.toJson(res));

        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendError(resp, "Invalid numeric format for ID: " + idParam);
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendError(resp, "Database query error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo != null) {
            if (pathInfo.equals("/update")) {
                doPut(req, resp);
                return;
            } else if (pathInfo.equals("/delete")) {
                doDelete(req, resp);
                return;
            }
        }

        // Add Book Listing
        User currentUser = getAuthenticatedUser(req);
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            sendError(resp, "Authentication required. Please sign in to list a textbook.");
            return;
        }

        JsonObject payload = parseRequestBody(req);
        Book newBook = gson.fromJson(payload, Book.class);

        if (newBook == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendError(resp, "Empty or invalid book listing data.");
            return;
        }

        try {
            // Securely assign owner ID from authenticated session
            boolean created = bookService.createBookListing(newBook, currentUser.getId());

            if (created) {
                resp.setStatus(HttpServletResponse.SC_CREATED);
                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.addProperty("message", "Book listing created successfully!");
                res.addProperty("bookId", newBook.getId());
                res.add("book", gson.toJsonTree(newBook));
                try (PrintWriter out = resp.getWriter()) {
                    out.print(gson.toJson(res));
                }
            } else {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                sendError(resp, "Failed to persist book listing in database.");
            }

        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendError(resp, e.getMessage());
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendError(resp, "Database error: " + e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        User currentUser = getAuthenticatedUser(req);
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            sendError(resp, "Authentication required. Please sign in to edit listings.");
            return;
        }

        JsonObject payload = parseRequestBody(req);
        Book book = gson.fromJson(payload, Book.class);

        if (book == null || book.getId() == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendError(resp, "Book ID is required for editing listing.");
            return;
        }

        try {
            boolean updated = bookService.updateBookListing(book, currentUser.getId());

            if (updated) {
                resp.setStatus(HttpServletResponse.SC_OK);
                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.addProperty("message", "Book listing updated successfully!");
                try (PrintWriter out = resp.getWriter()) {
                    out.print(gson.toJson(res));
                }
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                sendError(resp, "Book listing not found or could not be updated.");
            }

        } catch (SecurityException e) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            sendError(resp, e.getMessage());
        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendError(resp, e.getMessage());
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendError(resp, "Database error updating book: " + e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        User currentUser = getAuthenticatedUser(req);
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            sendError(resp, "Authentication required. Please sign in to delete listings.");
            return;
        }

        String idParam = req.getParameter("id");
        if (idParam == null || idParam.isBlank()) {
            JsonObject payload = parseRequestBody(req);
            if (payload.has("id")) {
                idParam = payload.get("id").getAsString();
            }
        }

        if (idParam == null || idParam.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendError(resp, "Book ID is required for deletion.");
            return;
        }

        try {
            Long bookId = Long.parseLong(idParam);
            boolean deleted = bookService.deleteBookListing(bookId, currentUser.getId());

            if (deleted) {
                resp.setStatus(HttpServletResponse.SC_OK);
                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.addProperty("message", "Book listing deleted successfully.");
                try (PrintWriter out = resp.getWriter()) {
                    out.print(gson.toJson(res));
                }
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                sendError(resp, "Book listing not found or already deleted.");
            }

        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendError(resp, "Invalid numeric format for book ID: " + idParam);
        } catch (SecurityException e) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            sendError(resp, e.getMessage());
        } catch (IllegalStateException e) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            sendError(resp, e.getMessage());
        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            sendError(resp, e.getMessage());
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendError(resp, "Database error deleting book: " + e.getMessage());
        }
    }

    private User getAuthenticatedUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("currentUser") != null) {
            return (User) session.getAttribute("currentUser");
        }
        return null;
    }

    private JsonObject parseRequestBody(HttpServletRequest req) {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            if (!sb.toString().trim().isEmpty()) {
                JsonObject json = gson.fromJson(sb.toString(), JsonObject.class);
                if (json != null) {
                    return json;
                }
            }
        } catch (Exception ignored) {
        }
        return new JsonObject();
    }

    private JsonObject createErrorJson(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("success", false);
        err.addProperty("error", message);
        return err;
    }

    private void sendError(HttpServletResponse resp, String message) throws IOException {
        try (PrintWriter out = resp.getWriter()) {
            out.print(gson.toJson(createErrorJson(message)));
        }
    }
}
