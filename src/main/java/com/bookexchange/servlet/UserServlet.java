package com.bookexchange.servlet;

import com.bookexchange.config.DBConnection;
import com.bookexchange.model.User;
import com.bookexchange.service.UserService;
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

    private final UserService userService;
    private final Gson gson;

    public UserServlet() {
        this.userService = new UserService();
        this.gson = new Gson();
    }

    public UserServlet(UserService userService) {
        this.userService = userService;
        this.gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String idParam = req.getParameter("id");
        try (PrintWriter out = resp.getWriter()) {
            if (idParam == null || idParam.isBlank()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = createErrorJson("Parameter 'id' is required.");
                out.print(gson.toJson(err));
                return;
            }

            if (!ValidationUtil.isValidPositiveId(idParam)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = createErrorJson("Invalid numeric ID format: " + idParam);
                out.print(gson.toJson(err));
                return;
            }

            Long id = Long.parseLong(idParam.trim());
            User user = userService.getStudentById(id);
            if (user != null) {
                // Do not expose password hash in response
                user.setPasswordHash(null);
                out.print(gson.toJson(user));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject err = createErrorJson("Student not found with ID: " + id);
                out.print(gson.toJson(err));
            }

        } catch (SQLException e) {
            System.err.println(DBConnection.formatSqlException("UserServlet.doGet", e));
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject err = createErrorJson("Unable to retrieve student record due to an internal database error. Please try again later.");
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
            String body = sb.toString().trim();
            if (body.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = createErrorJson("Empty JSON payload");
                out.print(gson.toJson(err));
                return;
            }

            JsonObject json;
            try {
                JsonElement elem = JsonParser.parseString(body);
                if (elem == null || !elem.isJsonObject()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject err = createErrorJson("Invalid JSON object payload");
                    out.print(gson.toJson(err));
                    return;
                }
                json = elem.getAsJsonObject();
            } catch (JsonSyntaxException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject err = createErrorJson("Malformed JSON request payload.");
                out.print(gson.toJson(err));
                return;
            }

            String universityId = json.has("universityId") && !json.get("universityId").isJsonNull()
                    ? json.get("universityId").getAsString().trim() : "";
            String fullName = json.has("fullName") && !json.get("fullName").isJsonNull()
                    ? json.get("fullName").getAsString().trim() : "";
            String email = json.has("email") && !json.get("email").isJsonNull()
                    ? json.get("email").getAsString().trim() : "";
            String phone = json.has("phone") && !json.get("phone").isJsonNull()
                    ? json.get("phone").getAsString().trim() : "";
            String rawPassword = json.has("password") && !json.get("password").isJsonNull()
                    ? json.get("password").getAsString() : "";
            String department = json.has("department") && !json.get("department").isJsonNull()
                    ? json.get("department").getAsString().trim() : "General";
            int semester = json.has("semester") && !json.get("semester").isJsonNull()
                    ? json.get("semester").getAsInt() : 1;
            String campus = json.has("campusName") && !json.get("campusName").isJsonNull()
                    ? json.get("campusName").getAsString().trim() : "Main Campus";

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
            JsonObject err = createErrorJson(e.getMessage());
            resp.getWriter().print(gson.toJson(err));
        } catch (SQLException e) {
            System.err.println(DBConnection.formatSqlException("UserServlet.doPost", e));
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject err = createErrorJson("Unable to process student registration due to an internal database error. Please try again later.");
            resp.getWriter().print(gson.toJson(err));
        }
    }

    private JsonObject createErrorJson(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("success", false);
        err.addProperty("error", message);
        return err;
    }
}
