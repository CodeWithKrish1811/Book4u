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
 * ProfileServlet - Student Profile Management & Secure Password Change
 * ============================================================================
 * Endpoint: /api/profile/*
 * Handles:
 * - GET  /api/profile           : Fetch authenticated student profile details
 * - POST /api/profile/update    : Update permitted profile fields
 * - POST /api/profile/password  : Secure password update workflow
 * ============================================================================
 */
@WebServlet(name = "ProfileServlet", urlPatterns = "/api/profile/*")
public class ProfileServlet extends HttpServlet {

    private final UserService userService = new UserService();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User currentUser = getAuthenticatedUser(req);
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            sendJson(resp, createErrorJson("Unauthorized. Please log in first."));
            return;
        }

        try {
            // Retrieve fresh record from database
            User freshProfile = userService.getStudentById(currentUser.getId());
            if (freshProfile == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                sendJson(resp, createErrorJson("Student profile record not found."));
                return;
            }

            resp.setStatus(HttpServletResponse.SC_OK);
            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.add("student", gson.toJsonTree(freshProfile));
            sendJson(resp, res);

        } catch (SQLException e) {
            System.err.println(DBConnection.formatSqlException("ProfileServlet.doGet", e));
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendJson(resp, createErrorJson("Unable to load student profile due to an internal database error. Please try again later."));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User currentUser = getAuthenticatedUser(req);
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            sendJson(resp, createErrorJson("Unauthorized. Please log in first."));
            return;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("") || pathInfo.equals("/update")) {
            handleProfileUpdate(req, resp, currentUser);
        } else if (pathInfo.equals("/password") || pathInfo.equals("/change-password")) {
            handlePasswordChange(req, resp, currentUser);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            sendJson(resp, createErrorJson("Endpoint not found: " + pathInfo));
        }
    }

    /**
     * Updates permitted profile information (Name, Phone, Department, Semester, Campus).
     * University ID and Email are intentionally immutable.
     */
    private void handleProfileUpdate(HttpServletRequest req, HttpServletResponse resp, User currentUser)
            throws IOException {

        JsonObject payload = parseRequestBody(req);

        String fullName = getString(payload, req, "fullName");
        String phone = getString(payload, req, "phone");
        String department = getString(payload, req, "department");
        int semester = getInt(payload, req, "semester", currentUser.getSemester());
        String campusName = getString(payload, req, "campusName");
        if (campusName.isEmpty()) {
            campusName = currentUser.getCampusName();
        }

        try {
            User updated = userService.updateProfile(
                    currentUser.getId(), fullName, phone, department, semester, campusName
            );

            // Update session user
            HttpSession session = req.getSession(false);
            if (session != null) {
                session.setAttribute("currentUser", updated);
                session.setAttribute("userName", updated.getFullName());
            }

            resp.setStatus(HttpServletResponse.SC_OK);
            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("message", "Profile updated successfully!");
            res.add("student", gson.toJsonTree(updated));
            sendJson(resp, res);

        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendJson(resp, createErrorJson(e.getMessage()));
        } catch (SQLException e) {
            System.err.println(DBConnection.formatSqlException("ProfileServlet.updateProfile", e));
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendJson(resp, createErrorJson("Unable to update profile due to an internal database error. Please try again later."));
        }
    }

    /**
     * Handles secure password change workflow.
     */
    private void handlePasswordChange(HttpServletRequest req, HttpServletResponse resp, User currentUser)
            throws IOException {

        JsonObject payload = parseRequestBody(req);

        String currentPassword = getString(payload, req, "currentPassword");
        String newPassword = getString(payload, req, "newPassword");
        String confirmPassword = getString(payload, req, "confirmPassword");

        try {
            userService.changePassword(currentUser.getId(), currentPassword, newPassword, confirmPassword);

            resp.setStatus(HttpServletResponse.SC_OK);
            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("message", "Password changed successfully! Please use your new password for future logins.");
            sendJson(resp, res);

        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendJson(resp, createErrorJson(e.getMessage()));
        } catch (SQLException e) {
            System.err.println(DBConnection.formatSqlException("ProfileServlet.changePassword", e));
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendJson(resp, createErrorJson("Unable to change password due to an internal database error. Please try again later."));
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
