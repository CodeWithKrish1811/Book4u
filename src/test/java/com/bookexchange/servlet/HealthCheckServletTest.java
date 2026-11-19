package com.bookexchange.servlet;

import com.bookexchange.config.DBConnection;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class HealthCheckServletTest {

    private final Gson gson = new Gson();

    @AfterEach
    void tearDown() {
        DBConnection.resetTestConnectionOverride();
    }

    private HttpServletRequest createMockRequest(String uri) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getRequestURI".equals(name)) {
                        return uri;
                    }
                    return null;
                }
        );
    }

    private HttpServletResponse createMockResponse(StringWriter stringWriter, int[] statusHolder) {
        PrintWriter printWriter = new PrintWriter(stringWriter);
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getWriter".equals(name)) {
                        return printWriter;
                    } else if ("setStatus".equals(name)) {
                        statusHolder[0] = (Integer) args[0];
                        return null;
                    }
                    return null;
                }
        );
    }

    @Test
    @DisplayName("HealthCheckServlet: Liveness check (/api/health/live) returns HTTP 200 even when database is disconnected")
    void testHealthCheckLivenessAlwaysReturns200() throws Exception {
        // Liveness verifies application/container is running; it must NOT fail when DB is disconnected
        HealthCheckServlet servlet = new HealthCheckServlet(() -> false);

        StringWriter sw = new StringWriter();
        int[] status = new int[]{0};

        servlet.doGet(createMockRequest("/api/health/live"), createMockResponse(sw, status));

        assertEquals(HttpServletResponse.SC_OK, status[0], "Liveness probe must return HTTP 200");
        JsonObject json = gson.fromJson(sw.toString(), JsonObject.class);
        assertEquals("UP", json.get("status").getAsString());
        assertEquals("liveness", json.get("check").getAsString());
        assertEquals("Apache Tomcat 10.1+ (Jakarta Servlet 6.0)", json.get("server").getAsString());
        assertTrue(json.has("project"));
        assertTrue(json.has("timestamp"));
    }

    @Test
    @DisplayName("HealthCheckServlet: Readiness check (/api/health/ready) returns HTTP 200 when database is connected")
    void testHealthCheckReadinessReturns200WhenConnected() throws Exception {
        HealthCheckServlet servlet = new HealthCheckServlet(() -> true);

        StringWriter sw = new StringWriter();
        int[] status = new int[]{0};

        servlet.doGet(createMockRequest("/api/health/ready"), createMockResponse(sw, status));

        assertEquals(HttpServletResponse.SC_OK, status[0], "Readiness probe must return HTTP 200 when DB connected");
        JsonObject json = gson.fromJson(sw.toString(), JsonObject.class);
        assertEquals("UP", json.get("status").getAsString());
        assertEquals("readiness", json.get("check").getAsString());
        assertEquals("CONNECTED", json.get("databaseStatus").getAsString());
    }

    @Test
    @DisplayName("HealthCheckServlet: Readiness check (/api/health/ready) returns HTTP 503 when database is disconnected")
    void testHealthCheckReadinessReturns503WhenDisconnected() throws Exception {
        HealthCheckServlet servlet = new HealthCheckServlet(() -> false);

        StringWriter sw = new StringWriter();
        int[] status = new int[]{0};

        servlet.doGet(createMockRequest("/api/health/ready"), createMockResponse(sw, status));

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, status[0], "Readiness probe must return HTTP 503 when DB disconnected");
        JsonObject json = gson.fromJson(sw.toString(), JsonObject.class);
        assertEquals("DEGRADED", json.get("status").getAsString());
        assertEquals("readiness", json.get("check").getAsString());
        assertEquals("DISCONNECTED", json.get("databaseStatus").getAsString());
    }

    @Test
    @DisplayName("HealthCheckServlet: Legacy full health check (/api/health) returns HTTP 200 when healthy")
    void testHealthCheckLegacyFullReturns200() throws Exception {
        HealthCheckServlet servlet = new HealthCheckServlet(() -> true);

        StringWriter sw = new StringWriter();
        int[] status = new int[]{0};

        servlet.doGet(createMockRequest("/api/health"), createMockResponse(sw, status));

        assertEquals(HttpServletResponse.SC_OK, status[0]);
        JsonObject json = gson.fromJson(sw.toString(), JsonObject.class);
        assertEquals("UP", json.get("status").getAsString());
        assertEquals("full", json.get("check").getAsString());
        assertEquals("CONNECTED", json.get("databaseStatus").getAsString());
    }

    @Test
    @DisplayName("HealthCheckServlet: Legacy full health check (/api/health) returns HTTP 503 when disconnected")
    void testHealthCheckLegacyFullReturns503() throws Exception {
        HealthCheckServlet servlet = new HealthCheckServlet(() -> false);

        StringWriter sw = new StringWriter();
        int[] status = new int[]{0};

        servlet.doGet(createMockRequest("/api/health"), createMockResponse(sw, status));

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, status[0]);
        JsonObject json = gson.fromJson(sw.toString(), JsonObject.class);
        assertEquals("DEGRADED", json.get("status").getAsString());
        assertEquals("full", json.get("check").getAsString());
        assertEquals("DISCONNECTED", json.get("databaseStatus").getAsString());
    }

    @Test
    @DisplayName("HealthCheckServlet: Thread isolation ensures concurrent requests do not collide")
    void testHealthCheckConcurrentIsolation() throws Exception {
        HealthCheckServlet servlet = new HealthCheckServlet(DBConnection::testConnection);

        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(2);

        AtomicBoolean thread1Success = new AtomicBoolean(false);
        AtomicBoolean thread2Success = new AtomicBoolean(false);

        Thread thread1 = new Thread(() -> {
            try {
                startSignal.await();
                DBConnection.setTestConnectionOverride(true);
                StringWriter sw = new StringWriter();
                int[] st = new int[]{0};
                servlet.doGet(createMockRequest("/api/health/ready"), createMockResponse(sw, st));
                if (st[0] == 200 && sw.toString().contains("\"status\":\"UP\"")) {
                    thread1Success.set(true);
                }
            } catch (Exception ignored) {
            } finally {
                DBConnection.resetTestConnectionOverride();
                doneSignal.countDown();
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                startSignal.await();
                DBConnection.setTestConnectionOverride(false);
                StringWriter sw = new StringWriter();
                int[] st = new int[]{0};
                servlet.doGet(createMockRequest("/api/health/ready"), createMockResponse(sw, st));
                if (st[0] == 503 && sw.toString().contains("\"status\":\"DEGRADED\"")) {
                    thread2Success.set(true);
                }
            } catch (Exception ignored) {
            } finally {
                DBConnection.resetTestConnectionOverride();
                doneSignal.countDown();
            }
        });

        thread1.start();
        thread2.start();

        startSignal.countDown();
        doneSignal.await();

        assertTrue(thread1Success.get(), "Thread 1 must receive HTTP 200 / UP independently");
        assertTrue(thread2Success.get(), "Thread 2 must receive HTTP 503 / DEGRADED independently");
    }
}
