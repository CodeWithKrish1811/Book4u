package com.bookexchange.servlet;

import com.bookexchange.config.DBConnection;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * HealthCheckServlet
 * ============================================================================
 * Endpoint: GET /api/health
 * Verifies that the Java Servlet container (Apache Tomcat 10.1+) is running
 * and tests live JDBC database connectivity to MySQL.
 * Demonstrates: HttpServlet lifecycle, doGet method, JSON serialization with Gson.
 * ============================================================================
 */
@WebServlet(name = "HealthCheckServlet", urlPatterns = "/api/health")
public class HealthCheckServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        boolean dbConnected = DBConnection.testConnection();

        JsonObject responseJson = new JsonObject();
        responseJson.addProperty("status", "UP");
        responseJson.addProperty("project", "Student Peer-to-Peer Book Exchange System (Book4u)");
        responseJson.addProperty("server", "Apache Tomcat 10.1+ (Jakarta Servlet 6.0)");
        responseJson.addProperty("javaVersion", System.getProperty("java.version"));
        responseJson.addProperty("databaseStatus", dbConnected ? "CONNECTED" : "DISCONNECTED");
        responseJson.addProperty("timestamp", LocalDateTime.now().toString());

        try (PrintWriter out = resp.getWriter()) {
            out.print(gson.toJson(responseJson));
            out.flush();
        }
    }
}
