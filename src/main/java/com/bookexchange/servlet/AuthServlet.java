package com.bookexchange.servlet;

import com.bookexchange.config.DBConnection;
import com.bookexchange.model.User;
import com.bookexchange.service.UserService;
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

/**
 * ============================================================================
 * AuthServlet - Student Authentication and Session Lifecycle Servlet
 * ============================================================================
 * Handles:
 * - POST /api/auth/register : Student account registration
 * - POST /api/auth/login    : Email/password authentication and session creation
 * - POST /api/auth/logout   : HTTP session invalidation
 * - GET  /api/auth/logout   : Browser logout with redirect
 * - GET  /api/auth/status   : Check active session authentication state
 * ============================================================================
 */
@WebServlet(name = "AuthServlet", urlPatterns = "/api/auth/*")
public class AuthServlet extends HttpServlet {

    private final UserService userService = new UserService();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            pathInfo = "";
        }

        switch (pathInfo) {
            case "/status":
            case "/me":
                handleStatus(req, resp);
                break;

            case "/logout":
                handleLogout(req, resp, true);
                break;

            default:
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                sendJson(resp, createErrorJson("Endpoint not found: " + pathInfo));
                break;
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            pathInfo = "";
        }

        switch (pathInfo) {
            case "/register":
                handleRegister(req, resp);
                break;

            case "/login":
                handleLogin(req, resp);
                break;

            case "/logout":
                handleLogout(req, resp, false);
                break;

            default:
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                sendJson(resp, createErrorJson("Endpoint not found: " + pathInfo));
                break;
        }
    }

    /**
     * Handles student account registration.
     */
    private void handleRegister(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject payload = parseRequestBody(req);

        String fullName = getString(payload, req, "fullName");
        String universityId = getString(payload, req, "universityId");
        String email = getString(payload, req, "email");
        String phone = getString(payload, req, "phone");
        String department = getString(payload, req, "department");
        String campusName = getString(payload, req, "campusName");
        if (campusName.isEmpty()) {
            campusName = "Main Campus";
        }
        int semester = getInt(payload, req, "semester", 1);
        String password = getString(payload, req, "password");
        String confirmPassword = getString(payload, req, "confirmPassword");

        User newUser = new User(universityId, fullName, email, phone, null, department, semester, campusName);

        try {
            User registered = userService.registerStudent(newUser, password, confirmPassword);

            resp.setStatus(HttpServletResponse.SC_CREATED);
            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("message", "Registration successful! You can now log in.");
            res.addProperty("studentId", registered.getId());
            res.addProperty("fullName", registered.getFullName());
            res.addProperty("email", registered.getEmail());
            sendJson(resp, res);

        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendJson(resp, createErrorJson(e.getMessage()));
        } catch (SQLException e) {
            System.err.println(DBConnection.formatSqlException("AuthServlet.register", e));
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendJson(resp, createErrorJson("Registration could not be completed due to an internal database error. Please try again later."));
        }
    }

    /**
     * Handles student login and HTTP session creation.
     */
    private void handleLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject payload = parseRequestBody(req);

        String email = getString(payload, req, "email");
        String password = getString(payload, req, "password");

        if (email.isBlank() || password.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendJson(resp, createErrorJson("Email and password are required."));
            return;
        }

        try {
            User student = userService.authenticate(email, password);

            if (student != null) {
                // Invalidate any existing session to prevent Session Fixation attacks
                HttpSession oldSession = req.getSession(false);
                if (oldSession != null) {
                    oldSession.invalidate();
                }

                // Create fresh new authenticated session
                HttpSession session = req.getSession(true);
                session.setAttribute("currentUser", student);
                session.setAttribute("userId", student.getId());
                session.setAttribute("userEmail", student.getEmail());
                session.setAttribute("userName", student.getFullName());
                session.setMaxInactiveInterval(30 * 60); // 30 minutes timeout

                resp.setStatus(HttpServletResponse.SC_OK);
                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.addProperty("message", "Login successful! Welcome, " + student.getFullName() + ".");
                res.add("user", gson.toJsonTree(student));
                sendJson(resp, res);

            } else {
                // Authentication failed
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                sendJson(resp, createErrorJson("Invalid college email or password. Please check your credentials."));
            }

        } catch (SQLException e) {
            System.err.println(DBConnection.formatSqlException("AuthServlet.login", e));
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendJson(resp, createErrorJson("Authentication service is temporarily unavailable. Please try again later."));
        }
    }

    /**
     * Handles session invalidation and logout.
     */
    private void handleLogout(HttpServletRequest req, HttpServletResponse resp, boolean isBrowserRedirect) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.removeAttribute("currentUser");
            session.removeAttribute("userId");
            session.removeAttribute("userEmail");
            session.removeAttribute("userName");
            session.invalidate();
        }

        // Expire the session cookie in browser client
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("JSESSIONID", "");
        cookie.setPath(req.getContextPath().isEmpty() ? "/" : req.getContextPath());
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        if (DBConnection.isProductionEnvironment()) {
            cookie.setSecure(true);
        }
        resp.addCookie(cookie);

        if (isBrowserRedirect) {
            String contextPath = req.getContextPath();
            resp.sendRedirect(contextPath + "/login.html?loggedOut=true");
        } else {
            resp.setStatus(HttpServletResponse.SC_OK);
            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("message", "Logged out successfully.");
            sendJson(resp, res);
        }
    }

    /**
     * Returns the current session authentication status.
     */
    private void handleStatus(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        JsonObject res = new JsonObject();

        if (session != null && session.getAttribute("currentUser") != null) {
            User user = (User) session.getAttribute("currentUser");
            res.addProperty("authenticated", true);
            res.add("user", gson.toJsonTree(user));
        } else {
            res.addProperty("authenticated", false);
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        sendJson(resp, res);
    }

    /**
     * Utility to extract JSON or fallback to request parameters.
     */
    private JsonObject parseRequestBody(HttpServletRequest req) {
        String contentType = req.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
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
        }
        return new JsonObject();
    }

    private String getString(JsonObject json, HttpServletRequest req, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString().trim();
        }
        String param = req.getParameter(key);
        return param != null ? param.trim() : "";
    }

    private int getInt(JsonObject json, HttpServletRequest req, String key, int defaultValue) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            try {
                return json.get(key).getAsInt();
            } catch (Exception ignored) {
            }
        }
        String param = req.getParameter(key);
        if (param != null) {
            try {
                return Integer.parseInt(param.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private JsonObject createErrorJson(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("success", false);
        err.addProperty("error", message);
        return err;
    }

    private void sendJson(HttpServletResponse resp, JsonObject json) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.print(gson.toJson(json));
        }
    }
}
