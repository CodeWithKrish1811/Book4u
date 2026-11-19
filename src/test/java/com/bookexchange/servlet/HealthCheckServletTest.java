package com.bookexchange.servlet;

import com.bookexchange.config.DBConnection;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

public class HealthCheckServletTest {

    private final Gson gson = new Gson();

    private HttpServletRequest createMockRequest() {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> null
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
    @DisplayName("HealthCheckServlet returns HTTP 200 and UP status when database is connected")
    void testHealthCheckHealthyReturns200() throws Exception {
        // Mock connected database state without requiring a running MySQL instance
        HealthCheckServlet healthyServlet = new HealthCheckServlet(() -> true);

        StringWriter stringWriter = new StringWriter();
        int[] statusHolder = new int[]{0};

        HttpServletRequest request = createMockRequest();
        HttpServletResponse response = createMockResponse(stringWriter, statusHolder);

        healthyServlet.doGet(request, response);

        assertEquals(HttpServletResponse.SC_OK, statusHolder[0], "Expected HTTP 200 when database is connected");

        String jsonString = stringWriter.toString();
        assertNotNull(jsonString);
        assertFalse(jsonString.isBlank());

        JsonObject json = gson.fromJson(jsonString, JsonObject.class);
        assertEquals("UP", json.get("status").getAsString());
        assertEquals("CONNECTED", json.get("databaseStatus").getAsString());
        assertEquals("Apache Tomcat 10.1+ (Jakarta Servlet 6.0)", json.get("server").getAsString());
        assertTrue(json.has("project"));
        assertTrue(json.has("javaVersion"));
        assertTrue(json.has("environment"));
        assertTrue(json.has("timestamp"));
    }

    @Test
    @DisplayName("HealthCheckServlet returns HTTP 503 and DEGRADED status when database is disconnected")
    void testHealthCheckDegradedReturns503() throws Exception {
        // Mock disconnected database state without requiring a running MySQL instance
        HealthCheckServlet degradedServlet = new HealthCheckServlet(() -> false);

        StringWriter stringWriter = new StringWriter();
        int[] statusHolder = new int[]{0};

        HttpServletRequest request = createMockRequest();
        HttpServletResponse response = createMockResponse(stringWriter, statusHolder);

        degradedServlet.doGet(request, response);

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, statusHolder[0],
                "Expected HTTP 503 when database is disconnected");

        String jsonString = stringWriter.toString();
        assertNotNull(jsonString);
        assertFalse(jsonString.isBlank());

        JsonObject json = gson.fromJson(jsonString, JsonObject.class);
        assertEquals("DEGRADED", json.get("status").getAsString());
        assertEquals("DISCONNECTED", json.get("databaseStatus").getAsString());
        assertEquals("Apache Tomcat 10.1+ (Jakarta Servlet 6.0)", json.get("server").getAsString());
        assertTrue(json.has("project"));
        assertTrue(json.has("javaVersion"));
        assertTrue(json.has("environment"));
        assertTrue(json.has("timestamp"));
    }

    @Test
    @DisplayName("HealthCheckServlet default constructor honors DBConnection test override hook")
    void testHealthCheckDefaultConstructorWithOverride() throws Exception {
        try {
            // Test 1: Override to true
            DBConnection.setTestConnectionOverride(true);
            HealthCheckServlet servlet = new HealthCheckServlet();

            StringWriter sw1 = new StringWriter();
            int[] status1 = new int[]{0};
            servlet.doGet(createMockRequest(), createMockResponse(sw1, status1));
            assertEquals(HttpServletResponse.SC_OK, status1[0]);
            JsonObject json1 = gson.fromJson(sw1.toString(), JsonObject.class);
            assertEquals("UP", json1.get("status").getAsString());

            // Test 2: Override to false
            DBConnection.setTestConnectionOverride(false);
            StringWriter sw2 = new StringWriter();
            int[] status2 = new int[]{0};
            servlet.doGet(createMockRequest(), createMockResponse(sw2, status2));
            assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, status2[0]);
            JsonObject json2 = gson.fromJson(sw2.toString(), JsonObject.class);
            assertEquals("DEGRADED", json2.get("status").getAsString());

        } finally {
            DBConnection.resetTestConnectionOverride();
        }
    }
}
