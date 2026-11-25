package com.bookexchange.servlet;

import com.bookexchange.config.DBConnection;
import com.bookexchange.model.Book;
import com.bookexchange.model.User;
import com.bookexchange.service.BookService;
import com.bookexchange.util.ValidationUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
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
import java.util.Locale;

/**
 * ============================================================================
 * BookServlet - REST Controller for Academic Book Management & Discovery
 * ============================================================================
 * Endpoints:
 * - GET    /api/books                 : List available books / search & filter
 * - GET    /api/books?id=...          : View single book details
 * - GET    /api/books/{id}            : View single book details by path
 * - GET    /api/books?myBooks=true    : View authenticated student's listings
 * - GET    /api/books/my              : View authenticated student's listings (path alias)
 * - POST   /api/books                 : Add a new academic book listing
 * - PUT    /api/books                 : Update an existing book listing
 * - POST   /api/books/update          : Update book listing (POST alias)
 * - DELETE /api/books?id=...          : Delete a book listing
 * - DELETE /api/books/{id}            : Delete a book listing (path alias)
 * - POST   /api/books/delete          : Delete book listing (POST alias)
 * ============================================================================
 */
@WebServlet(name = "BookServlet", urlPatterns = {"/api/books", "/api/books/*"})
public class BookServlet extends HttpServlet {

    private final BookService bookService;
    private final Gson gson;

    public BookServlet() {
        this.bookService = new BookService();
        this.gson = new Gson();
    }

    public BookServlet(BookService bookService) {
        this.bookService = bookService;
        this.gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String idParam = req.getParameter("id");
        String myBooksParam = req.getParameter("myBooks");
        String query = req.getParameter("query");
        String title = req.getParameter("title");
        String author = req.getParameter("author");
        String isbn = req.getParameter("isbn");
        String subject = req.getParameter("subject");
        String department = req.getParameter("department");
        String courseCode = req.getParameter("courseCode");
        if (courseCode == null || courseCode.isBlank()) {
            courseCode = req.getParameter("course");
        }
        String condition = req.getParameter("condition");
        String mode = req.getParameter("mode");
        String status = req.getParameter("status");
        String excludeSelfParam = req.getParameter("excludeSelf");
        String pageParam = req.getParameter("page");
        String limitParam = req.getParameter("limit");

        boolean isPaginated = (pageParam != null && !pageParam.isBlank()) || (limitParam != null && !limitParam.isBlank());
        int page = 1;
        int limit = 20;
        if (isPaginated) {
            if (pageParam != null && !pageParam.isBlank()) {
                try {
                    page = Integer.parseInt(pageParam.trim());
                    if (page < 1) page = 1;
                } catch (NumberFormatException ignored) {
                    page = 1;
                }
            }
            if (limitParam != null && !limitParam.isBlank()) {
                try {
                    limit = Integer.parseInt(limitParam.trim());
                    if (limit < 1) limit = 20;
                    if (limit > 100) limit = 100;
                } catch (NumberFormatException ignored) {
                    limit = 20;
                }
            }
        }
        int offset = (page - 1) * limit;

        String pathInfo = req.getPathInfo();
        if (pathInfo != null) {
            pathInfo = pathInfo.trim();
        }

        User currentUser = getAuthenticatedUser(req);

        // Check if an ID is provided in pathInfo, e.g. /api/books/101
        if ((idParam == null || idParam.isBlank()) && pathInfo != null && !pathInfo.isEmpty()
                && !"/".equals(pathInfo) && !"/my".equalsIgnoreCase(pathInfo)) {
            String candidate = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
            if (!candidate.contains("/") && !candidate.isBlank()) {
                idParam = candidate;
            }
        }

        try (PrintWriter out = resp.getWriter()) {

            // 1. Single Book Details by ID
            if (idParam != null && !idParam.isBlank()) {
                if (!ValidationUtil.isValidPositiveId(idParam)) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print(gson.toJson(createErrorJson("Invalid numeric format for ID: " + idParam)));
                    return;
                }

                Long id = Long.parseLong(idParam.trim());
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

            // 2. My Books (Student's own listings via query param or /my endpoint)
            if ("true".equalsIgnoreCase(myBooksParam) || "/my".equalsIgnoreCase(pathInfo)) {
                if (currentUser == null) {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    out.print(gson.toJson(createErrorJson("Authentication required to view your listings.")));
                    return;
                }

                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                if (isPaginated) {
                    int totalCount = bookService.countMyBooks(currentUser.getId(), status);
                    List<Book> myBooks = bookService.getMyBooks(currentUser.getId(), status, offset, limit);
                    int totalPages = totalCount > 0 ? (int) Math.ceil((double) totalCount / limit) : 0;
                    res.add("books", gson.toJsonTree(myBooks));
                    res.addProperty("count", myBooks.size());
                    res.addProperty("totalCount", totalCount);
                    res.addProperty("page", page);
                    res.addProperty("pageSize", limit);
                    res.addProperty("totalPages", totalPages);
                } else {
                    List<Book> myBooks = bookService.getMyBooks(currentUser.getId(), status);
                    res.add("books", gson.toJsonTree(myBooks));
                    res.addProperty("count", myBooks.size());
                }
                out.print(gson.toJson(res));
                return;
            }

            // 3. Explore & Discovery Catalog (Search & Multi-Filter)
            Long excludeOwnerId = null;
            if ("true".equalsIgnoreCase(excludeSelfParam) && currentUser != null) {
                excludeOwnerId = currentUser.getId();
            }

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            if (isPaginated) {
                int totalCount = bookService.countBooks(query, title, author, isbn, subject, department, courseCode, condition, mode, excludeOwnerId);
                List<Book> books = bookService.searchAndFilterBooks(query, title, author, isbn, subject, department, courseCode, condition, mode, excludeOwnerId, offset, limit);
                int totalPages = totalCount > 0 ? (int) Math.ceil((double) totalCount / limit) : 0;
                res.add("books", gson.toJsonTree(books));
                res.addProperty("count", books.size());
                res.addProperty("totalCount", totalCount);
                res.addProperty("page", page);
                res.addProperty("pageSize", limit);
                res.addProperty("totalPages", totalPages);
            } else {
                List<Book> books = bookService.searchAndFilterBooks(query, title, author, isbn, subject, department, courseCode, condition, mode, excludeOwnerId, 0, 0);
                res.add("books", gson.toJsonTree(books));
                res.addProperty("count", books.size());
            }
            out.print(gson.toJson(res));

        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendError(resp, e.getMessage());
        } catch (SQLException e) {
            System.err.println(DBConnection.formatSqlException("BookServlet.doGet", e));
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendError(resp, "Unable to retrieve book records due to an internal database error. Please try again later.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo != null) {
            if (pathInfo.equals("/update") || pathInfo.startsWith("/update/")) {
                doPut(req, resp);
                return;
            } else if (pathInfo.equals("/delete") || pathInfo.startsWith("/delete/")) {
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

        JsonObject payload;
        try {
            payload = parseRequestBody(req);
        } catch (JsonSyntaxException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendError(resp, "Malformed JSON request payload.");
            return;
        }

        if (payload == null || payload.size() == 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendError(resp, "Empty or invalid book listing data.");
            return;
        }

        Book newBook;
        try {
            newBook = gson.fromJson(payload, Book.class);
        } catch (JsonSyntaxException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendError(resp, "Malformed book JSON structure.");
            return;
        }

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
            System.err.println(DBConnection.formatSqlException("BookServlet.doPost", e));
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendError(resp, "Unable to save book listing due to an internal database error. Please try again later.");
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

        JsonObject payload;
        try {
            payload = parseRequestBody(req);
        } catch (JsonSyntaxException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendError(resp, "Malformed JSON request payload.");
            return;
        }

        Book book;
        try {
            book = (payload != null && payload.size() > 0) ? gson.fromJson(payload, Book.class) : new Book();
        } catch (JsonSyntaxException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendError(resp, "Malformed book JSON structure.");
            return;
        }

        if (book == null) {
            book = new Book();
        }

        // Resolve Book ID from JSON, query param, or pathInfo
        if (book.getId() == null || book.getId() <= 0) {
            String idParam = req.getParameter("id");
            if (idParam == null || idParam.isBlank()) {
                String pathInfo = req.getPathInfo();
                if (pathInfo != null && !pathInfo.isEmpty() && !"/".equals(pathInfo) && !"/update".equalsIgnoreCase(pathInfo)) {
                    String candidate = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
                    if (candidate.startsWith("update/")) {
                        candidate = candidate.substring(7);
                    }
                    if (!candidate.contains("/") && !candidate.isBlank()) {
                        idParam = candidate;
                    }
                }
            }
            if ((idParam == null || idParam.isBlank()) && payload != null && payload.has("id") && !payload.get("id").isJsonNull()) {
                try {
                    idParam = payload.get("id").getAsString();
                } catch (Exception ignored) {
                }
            }

            if (ValidationUtil.isValidPositiveId(idParam)) {
                book.setId(Long.parseLong(idParam.trim()));
            } else if (idParam != null && !idParam.isBlank()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                sendError(resp, "Invalid numeric format for book ID: " + idParam);
                return;
            }
        }

        if (book.getId() == null || book.getId() <= 0) {
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
            if (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("not found")) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
            sendError(resp, e.getMessage());
        } catch (SQLException e) {
            System.err.println(DBConnection.formatSqlException("BookServlet.doPut", e));
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendError(resp, "Unable to update book listing due to an internal database error. Please try again later.");
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
            String pathInfo = req.getPathInfo();
            if (pathInfo != null && !pathInfo.isEmpty() && !"/".equals(pathInfo) && !"/delete".equalsIgnoreCase(pathInfo)) {
                String candidate = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
                if (candidate.startsWith("delete/")) {
                    candidate = candidate.substring(7);
                }
                if (!candidate.contains("/") && !candidate.isBlank()) {
                    idParam = candidate;
                }
            }
        }
        if (idParam == null || idParam.isBlank()) {
            JsonObject payload = parseRequestBody(req);
            if (payload != null && payload.has("id") && !payload.get("id").isJsonNull()) {
                try {
                    idParam = payload.get("id").getAsString();
                } catch (Exception ignored) {
                }
            }
        }

        if (idParam == null || idParam.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendError(resp, "Book ID is required for deletion.");
            return;
        }

        if (!ValidationUtil.isValidPositiveId(idParam)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendError(resp, "Invalid numeric format for book ID: " + idParam);
            return;
        }

        try {
            Long bookId = Long.parseLong(idParam.trim());
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
            if (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("not found")) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
            sendError(resp, e.getMessage());
        } catch (SQLException e) {
            System.err.println(DBConnection.formatSqlException("BookServlet.doDelete", e));
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendError(resp, "Unable to delete book listing due to an internal database error. Please try again later.");
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
            String raw = sb.toString().trim();
            if (!raw.isEmpty()) {
                JsonElement element = JsonParser.parseString(raw);
                if (element != null && element.isJsonObject()) {
                    return element.getAsJsonObject();
                }
            }
        } catch (JsonSyntaxException e) {
            throw e;
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
