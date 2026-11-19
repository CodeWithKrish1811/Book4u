package com.bookexchange.servlet;

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

    private final HealthCheckServlet servlet = new HealthCheckServlet();
    private final Gson gson = new Gson();

    @Test
    @DisplayName("HealthCheckServlet returns accurate JSON structure and appropriate HTTP status")
    void testHealthCheckServletResponse() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        int[] statusHolder = new int[]{200};

        // Create lightweight dynamic proxies for HttpServletRequest and HttpServletResponse
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> null
        );

        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
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

        servlet.doGet(request, response);
        printWriter.flush();

        String jsonString = stringWriter.toString();
        assertNotNull(jsonString);
        assertFalse(jsonString.isBlank());

        JsonObject json = gson.fromJson(jsonString, JsonObject.class);
        assertNotNull(json);
        assertTrue(json.has("status"));
        assertTrue(json.has("server"));
        assertTrue(json.has("databaseStatus"));
        assertTrue(json.has("environment"));

        // Status code must be either 200 (healthy) or 503 (disconnected)
        assertTrue(statusHolder[0] == 200 || statusHolder[0] == 503,
                "Status should be 200 when healthy or 503 when disconnected, got: " + statusHolder[0]);

        if (statusHolder[0] == 200) {
            assertEquals("UP", json.get("status").getAsString());
            assertEquals("CONNECTED", json.get("databaseStatus").getAsString());
        } else {
            assertEquals("DEGRADED", json.get("status").getAsString());
            assertEquals("DISCONNECTED", json.get("databaseStatus").getAsString());
        }
    }
}
