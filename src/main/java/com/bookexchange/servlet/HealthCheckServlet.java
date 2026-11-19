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
import java.util.function.BooleanSupplier;

/**
 * ============================================================================
 * HealthCheckServlet - System Diagnostic Endpoint
 * ============================================================================
 * Endpoint: GET /api/health
 *
 * Production Hardening:
 * 1. Returns HTTP 200 (OK) when Tomcat and MySQL are healthy and connected.
 * 2. Returns HTTP 503 (Service Unavailable) when the database is disconnected.
 * 3. Returns accurate JSON diagnostic metrics for cloud load balancers and container probes.
 * ============================================================================
 */
@WebServlet(name = "HealthCheckServlet", urlPatterns = "/api/health")
public class HealthCheckServlet extends HttpServlet {

    private final Gson gson = new Gson();
    private final BooleanSupplier dbConnectionChecker;

    public HealthCheckServlet() {
        this(DBConnection::testConnection);
    }

    // Package-private constructor for dependency injection during unit tests
    HealthCheckServlet(BooleanSupplier dbConnectionChecker) {
        this.dbConnectionChecker = (dbConnectionChecker != null) ? dbConnectionChecker : DBConnection::testConnection;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        boolean dbConnected = dbConnectionChecker.getAsBoolean();

        JsonObject responseJson = new JsonObject();
        if (dbConnected) {
            resp.setStatus(HttpServletResponse.SC_OK); // 200
            responseJson.addProperty("status", "UP");
        } else {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE); // 503
            responseJson.addProperty("status", "DEGRADED");
        }

        responseJson.addProperty("project", "Student Peer-to-Peer Book Exchange System (Book4u)");
        responseJson.addProperty("server", "Apache Tomcat 10.1+ (Jakarta Servlet 6.0)");
        responseJson.addProperty("javaVersion", System.getProperty("java.version"));
        responseJson.addProperty("databaseStatus", dbConnected ? "CONNECTED" : "DISCONNECTED");
        responseJson.addProperty("environment", DBConnection.isProductionEnvironment() ? "PRODUCTION" : "LOCAL");
        responseJson.addProperty("timestamp", LocalDateTime.now().toString());

        try (PrintWriter out = resp.getWriter()) {
            out.print(gson.toJson(responseJson));
            out.flush();
        }
    }
}
