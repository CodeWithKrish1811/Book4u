package com.bookexchange.servlet;

import com.bookexchange.model.User;
import com.bookexchange.service.UserService;
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

/**
 * ============================================================================
 * UserServlet - REST-Style Servlet for Student Account Operations
 * ============================================================================
 * Endpoint: /api/users
 * Handles:
 * - GET: Fetch student profile (by ?id=...)
 * - POST: Student registration
 * ============================================================================
 */
@WebServlet(name = "UserServlet", urlPatterns = "/api/users")
public class UserServlet extends HttpServlet {

    private final UserService userService = new UserService();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String idParam = req.getParameter("id");
        try (PrintWriter out = resp.getWriter()) {
            if (idParam == null || idParam.isBlank()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = new JsonObject();
                err.addProperty("error", "Parameter 'id' is required.");
                out.print(gson.toJson(err));
                return;
            }

            Long id = Long.parseLong(idParam);
            User user = userService.getStudentById(id);
            if (user != null) {
                // Do not expose password hash in response
                user.setPasswordHash(null);
                out.print(gson.toJson(user));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject err = new JsonObject();
                err.addProperty("error", "Student not found with ID: " + id);
                out.print(gson.toJson(err));
            }

        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject err = new JsonObject();
            err.addProperty("error", "Invalid numeric ID format: " + idParam);
            resp.getWriter().print(gson.toJson(err));
        } catch (SQLException e) {
            System.err.println("[UserServlet] SQL error in doGet: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject err = new JsonObject();
            err.addProperty("error", "Unable to retrieve student record due to an internal database error. Please try again later.");
            resp.getWriter().print(gson.toJson(err));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        try (PrintWriter out = resp.getWriter()) {
            JsonObject json = gson.fromJson(sb.toString(), JsonObject.class);

            if (json == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = new JsonObject();
                err.addProperty("error", "Empty JSON payload");
                out.print(gson.toJson(err));
                return;
            }

            String universityId = json.has("universityId") ? json.get("universityId").getAsString() : "";
            String fullName = json.has("fullName") ? json.get("fullName").getAsString() : "";
            String email = json.has("email") ? json.get("email").getAsString() : "";
            String phone = json.has("phone") ? json.get("phone").getAsString() : "";
            String rawPassword = json.has("password") ? json.get("password").getAsString() : "";
            String department = json.has("department") ? json.get("department").getAsString() : "General";
            int semester = json.has("semester") ? json.get("semester").getAsInt() : 1;
            String campus = json.has("campusName") ? json.get("campusName").getAsString() : "Main Campus";

            User newUser = new User(universityId, fullName, email, phone, null, department, semester, campus);
            User registered = userService.registerStudent(newUser, rawPassword);

            resp.setStatus(HttpServletResponse.SC_CREATED);
            JsonObject success = new JsonObject();
            success.addProperty("success", true);
            success.addProperty("message", "Student account registered successfully!");
            success.addProperty("studentId", registered.getId());
            out.print(gson.toJson(success));

        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject err = new JsonObject();
            err.addProperty("error", e.getMessage());
            resp.getWriter().print(gson.toJson(err));
        } catch (SQLException e) {
            System.err.println("[UserServlet] SQL error in doPost: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject err = new JsonObject();
            err.addProperty("error", "Unable to process student registration due to an internal database error. Please try again later.");
            resp.getWriter().print(gson.toJson(err));
        }
    }
}
