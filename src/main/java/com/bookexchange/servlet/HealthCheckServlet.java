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
 * HealthCheckServlet - System Diagnostic & Probe Endpoint
 * ============================================================================
 * Endpoints:
 * - GET /api/health/live  : Liveness probe (reports application/Tomcat status; HTTP 200 OK)
 * - GET /api/health/ready : Readiness probe (reports MySQL availability; HTTP 200 OK / 503 Service Unavailable)
 * - GET /api/health       : Full health check (backward-compatible; reports UP/DEGRADED with HTTP 200/503)
 * ============================================================================
 */
@WebServlet(name = "HealthCheckServlet", urlPatterns = {"/api/health", "/api/health/live", "/api/health/ready"})
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

        String uri = req.getRequestURI();
        boolean isLiveness = uri != null && uri.endsWith("/live");
        boolean isReadiness = uri != null && uri.endsWith("/ready");

        JsonObject responseJson = new JsonObject();
        responseJson.addProperty("project", "Student Peer-to-Peer Book Exchange System (Book4u)");
        responseJson.addProperty("server", "Apache Tomcat 10.1+ (Jakarta Servlet 6.0)");
        responseJson.addProperty("javaVersion", System.getProperty("java.version"));
        responseJson.addProperty("environment", DBConnection.isProductionEnvironment() ? "PRODUCTION" : "LOCAL");
        responseJson.addProperty("timestamp", LocalDateTime.now().toString());

        if (isLiveness) {
            // Liveness check: confirms servlet container and application are running and responsive
            resp.setStatus(HttpServletResponse.SC_OK); // 200
            responseJson.addProperty("status", "UP");
            responseJson.addProperty("check", "liveness");
        } else {
            // Readiness check (/api/health/ready) or Legacy Health Check (/api/health)
            boolean dbConnected = dbConnectionChecker.getAsBoolean();
            if (dbConnected) {
                resp.setStatus(HttpServletResponse.SC_OK); // 200
                responseJson.addProperty("status", "UP");
                responseJson.addProperty("databaseStatus", "CONNECTED");
            } else {
                resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE); // 503
                responseJson.addProperty("status", "DEGRADED");
                responseJson.addProperty("databaseStatus", "DISCONNECTED");
            }
            responseJson.addProperty("check", isReadiness ? "readiness" : "full");
        }

        try (PrintWriter out = resp.getWriter()) {
            out.print(gson.toJson(responseJson));
            out.flush();
        }
    }
}
