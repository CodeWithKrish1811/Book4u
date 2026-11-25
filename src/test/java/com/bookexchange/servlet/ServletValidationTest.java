package com.bookexchange.servlet;

import com.bookexchange.model.User;
import com.bookexchange.service.BookService;
import com.bookexchange.service.ExchangeRequestService;
import com.bookexchange.service.ExchangeService;
import com.bookexchange.service.UserService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Servlet Input Validation & Error Handling Tests")
class ServletValidationTest {

    private final Gson gson = new Gson();
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("CS-2024-001", "Aarav Sharma", "aarav.sharma@campus.edu",
                "9876543210", null, "Computer Science", 4, "Main Campus");
        testUser.setId(1L);
    }

    // -------------------------------------------------------------------------
    // Dynamic Mock Helpers
    // -------------------------------------------------------------------------
    private HttpSession createMockSession(User user) {
        Map<String, Object> sessionAttributes = new HashMap<>();
        if (user != null) {
            sessionAttributes.put("currentUser", user);
            sessionAttributes.put("userId", user.getId());
            sessionAttributes.put("userEmail", user.getEmail());
            sessionAttributes.put("userName", user.getFullName());
        }
        return (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class[]{HttpSession.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getAttribute".equals(name)) {
                        return sessionAttributes.get(args[0]);
                    } else if ("setAttribute".equals(name)) {
                        sessionAttributes.put((String) args[0], args[1]);
                        return null;
                    } else if ("removeAttribute".equals(name)) {
                        sessionAttributes.remove(args[0]);
                        return null;
                    } else if ("invalidate".equals(name)) {
                        sessionAttributes.clear();
                        return null;
                    } else if ("setMaxInactiveInterval".equals(name)) {
                        return null;
                    }
                    return null;
                }
        );
    }

    private HttpServletRequest createMockRequest(String methodStr, String pathInfo,
                                                 Map<String, String> params, String body, User user) {
        HttpSession session = createMockSession(user);
        Map<String, String> parameterMap = params != null ? params : new HashMap<>();

        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getMethod".equals(name)) {
                        return methodStr;
                    } else if ("getPathInfo".equals(name)) {
                        return pathInfo;
                    } else if ("getParameter".equals(name)) {
                        return parameterMap.get(args[0]);
                    } else if ("getSession".equals(name)) {
                        if (args.length == 0 || (Boolean) args[0]) {
                            return session;
                        }
                        return user != null ? session : null;
                    } else if ("getReader".equals(name)) {
                        return new BufferedReader(new StringReader(body != null ? body : ""));
                    } else if ("getContentType".equals(name)) {
                        return "application/json";
                    } else if ("getContextPath".equals(name)) {
                        return "";
                    } else if ("getRequestURI".equals(name)) {
                        return "/api" + (pathInfo != null ? pathInfo : "");
                    }
                    return null;
                }
        );
    }

    private static class ResponseHolder {
        int status = 200;
        final StringWriter out = new StringWriter();
    }

    private HttpServletResponse createMockResponse(ResponseHolder holder) {
        PrintWriter writer = new PrintWriter(holder.out);
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("setStatus".equals(name)) {
                        holder.status = (Integer) args[0];
                        return null;
                    } else if ("getWriter".equals(name)) {
                        return writer;
                    } else if ("setContentType".equals(name) || "setCharacterEncoding".equals(name)
                            || "addCookie".equals(name) || "sendRedirect".equals(name)) {
                        return null;
                    }
                    return null;
                }
        );
    }

    // =========================================================================
    // 1. BookServlet Tests
    // =========================================================================
    @Test
    @DisplayName("BookServlet: GET with non-numeric ID returns HTTP 400 Bad Request with JSON error")
    void testBookServletGetInvalidId() throws Exception {
        BookServlet servlet = new BookServlet();
        ResponseHolder holder = new ResponseHolder();
        Map<String, String> params = Map.of("id", "not-a-number");
        HttpServletRequest req = createMockRequest("GET", null, params, null, testUser);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doGet(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertNotNull(json);
        assertFalse(json.get("success").getAsBoolean());
        assertTrue(json.get("error").getAsString().contains("Invalid numeric format"));
    }

    @Test
    @DisplayName("BookServlet: GET with negative ID returns HTTP 400 Bad Request")
    void testBookServletGetNegativeId() throws Exception {
        BookServlet servlet = new BookServlet();
        ResponseHolder holder = new ResponseHolder();
        Map<String, String> params = Map.of("id", "-5");
        HttpServletRequest req = createMockRequest("GET", null, params, null, testUser);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doGet(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
    }

    @Test
    @DisplayName("BookServlet: GET myBooks without authentication returns HTTP 401 Unauthorized")
    void testBookServletGetMyBooksUnauthenticated() throws Exception {
        BookServlet servlet = new BookServlet();
        ResponseHolder holder = new ResponseHolder();
        Map<String, String> params = Map.of("myBooks", "true");
        HttpServletRequest req = createMockRequest("GET", null, params, null, null);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doGet(req, resp);

        assertEquals(401, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
        assertTrue(json.get("error").getAsString().contains("Authentication required"));
    }

    @Test
    @DisplayName("BookServlet: POST without authentication returns HTTP 401 Unauthorized")
    void testBookServletPostUnauthenticated() throws Exception {
        BookServlet servlet = new BookServlet();
        ResponseHolder holder = new ResponseHolder();
        HttpServletRequest req = createMockRequest("POST", null, null, "{\"title\":\"Some Book\"}", null);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPost(req, resp);

        assertEquals(401, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
    }

    @Test
    @DisplayName("BookServlet: POST with malformed JSON body returns HTTP 400 Bad Request")
    void testBookServletPostMalformedJson() throws Exception {
        BookServlet servlet = new BookServlet();
        ResponseHolder holder = new ResponseHolder();
        HttpServletRequest req = createMockRequest("POST", null, null, "{title: malformed json", testUser);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPost(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
        assertTrue(json.get("error").getAsString().contains("Malformed JSON"));
    }

    @Test
    @DisplayName("BookServlet: POST with empty JSON body returns HTTP 400 Bad Request")
    void testBookServletPostEmptyJson() throws Exception {
        BookServlet servlet = new BookServlet();
        ResponseHolder holder = new ResponseHolder();
        HttpServletRequest req = createMockRequest("POST", null, null, "{}", testUser);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPost(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
    }

    @Test
    @DisplayName("BookServlet: POST with invalid ISBN returns HTTP 400 Bad Request")
    void testBookServletPostInvalidIsbn() throws Exception {
        BookServlet servlet = new BookServlet();
        ResponseHolder holder = new ResponseHolder();
        String body = "{\"title\":\"Valid Title\",\"author\":\"Valid Author\",\"subject\":\"CS\",\"isbn\":\"12345\",\"sharingMode\":\"SWAP\"}";
        HttpServletRequest req = createMockRequest("POST", null, null, body, testUser);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPost(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
        assertTrue(json.get("error").getAsString().contains("Invalid ISBN"));
    }

    @Test
    @DisplayName("BookServlet: PUT with missing ID returns HTTP 400 Bad Request")
    void testBookServletPutMissingId() throws Exception {
        BookServlet servlet = new BookServlet();
        ResponseHolder holder = new ResponseHolder();
        String body = "{\"title\":\"Updated Title\",\"author\":\"Updated Author\"}";
        HttpServletRequest req = createMockRequest("PUT", null, null, body, testUser);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPut(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
        assertTrue(json.get("error").getAsString().contains("Book ID is required"));
    }

    @Test
    @DisplayName("BookServlet: DELETE with missing ID returns HTTP 400 Bad Request")
    void testBookServletDeleteMissingId() throws Exception {
        BookServlet servlet = new BookServlet();
        ResponseHolder holder = new ResponseHolder();
        HttpServletRequest req = createMockRequest("DELETE", null, null, null, testUser);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doDelete(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
        assertTrue(json.get("error").getAsString().contains("Book ID is required"));
    }

    // =========================================================================
    // 2. AuthServlet Tests
    // =========================================================================
    @Test
    @DisplayName("AuthServlet: POST /register with malformed JSON returns HTTP 400")
    void testAuthServletRegisterMalformedJson() throws Exception {
        AuthServlet servlet = new AuthServlet();
        ResponseHolder holder = new ResponseHolder();
        HttpServletRequest req = createMockRequest("POST", "/register", null, "{invalid-json", null);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPost(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
        assertTrue(json.get("error").getAsString().contains("Malformed JSON"));
    }

    @Test
    @DisplayName("AuthServlet: POST /register with missing mandatory fields returns HTTP 400")
    void testAuthServletRegisterMissingFields() throws Exception {
        AuthServlet servlet = new AuthServlet();
        ResponseHolder holder = new ResponseHolder();
        String body = "{\"fullName\":\"\",\"email\":\"test@campus.edu\"}";
        HttpServletRequest req = createMockRequest("POST", "/register", null, body, null);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPost(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
    }

    @Test
    @DisplayName("AuthServlet: POST /register with mismatched passwords returns HTTP 400")
    void testAuthServletRegisterMismatchedPasswords() throws Exception {
        AuthServlet servlet = new AuthServlet();
        ResponseHolder holder = new ResponseHolder();
        String body = "{\"fullName\":\"John Doe\",\"universityId\":\"CS101\",\"email\":\"john@campus.edu\"," +
                "\"department\":\"CS\",\"semester\":1,\"password\":\"password123\",\"confirmPassword\":\"diffPassword\"}";
        HttpServletRequest req = createMockRequest("POST", "/register", null, body, null);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPost(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
        assertTrue(json.get("error").getAsString().contains("match"));
    }

    @Test
    @DisplayName("AuthServlet: POST /login with missing credentials returns HTTP 400")
    void testAuthServletLoginMissingCredentials() throws Exception {
        AuthServlet servlet = new AuthServlet();
        ResponseHolder holder = new ResponseHolder();
        String body = "{\"email\":\"\",\"password\":\"\"}";
        HttpServletRequest req = createMockRequest("POST", "/login", null, body, null);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPost(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
        assertTrue(json.get("error").getAsString().contains("required"));
    }

    @Test
    @DisplayName("AuthServlet: POST /login with invalid email format returns HTTP 400")
    void testAuthServletLoginInvalidEmailFormat() throws Exception {
        AuthServlet servlet = new AuthServlet();
        ResponseHolder holder = new ResponseHolder();
        String body = "{\"email\":\"not-an-email\",\"password\":\"secret123\"}";
        HttpServletRequest req = createMockRequest("POST", "/login", null, body, null);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPost(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
        assertTrue(json.get("error").getAsString().contains("Invalid college email"));
    }

    @Test
    @DisplayName("AuthServlet: GET /unknown returns HTTP 404 Not Found")
    void testAuthServletUnknownEndpoint() throws Exception {
        AuthServlet servlet = new AuthServlet();
        ResponseHolder holder = new ResponseHolder();
        HttpServletRequest req = createMockRequest("GET", "/unknown", null, null, null);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doGet(req, resp);

        assertEquals(404, holder.status);
    }

    // =========================================================================
    // 3. ExchangeServlet Tests
    // =========================================================================
    @Test
    @DisplayName("ExchangeServlet: GET unauthenticated returns HTTP 401")
    void testExchangeServletGetUnauthenticated() throws Exception {
        ExchangeServlet servlet = new ExchangeServlet();
        ResponseHolder holder = new ResponseHolder();
        HttpServletRequest req = createMockRequest("GET", null, null, null, null);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doGet(req, resp);

        assertEquals(401, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
    }

    @Test
    @DisplayName("ExchangeServlet: GET with invalid ID format returns HTTP 400")
    void testExchangeServletGetInvalidIdFormat() throws Exception {
        ExchangeServlet servlet = new ExchangeServlet();
        ResponseHolder holder = new ResponseHolder();
        HttpServletRequest req = createMockRequest("GET", "/invalid-id", null, null, testUser);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doGet(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
    }

    @Test
    @DisplayName("ExchangeServlet: POST unauthenticated returns HTTP 401")
    void testExchangeServletPostUnauthenticated() throws Exception {
        ExchangeServlet servlet = new ExchangeServlet();
        ResponseHolder holder = new ResponseHolder();
        HttpServletRequest req = createMockRequest("POST", "/1/confirm-handover", null, null, null);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPost(req, resp);

        assertEquals(401, holder.status);
    }

    @Test
    @DisplayName("ExchangeServlet: POST with invalid ID returns HTTP 400")
    void testExchangeServletPostInvalidId() throws Exception {
        ExchangeServlet servlet = new ExchangeServlet();
        ResponseHolder holder = new ResponseHolder();
        HttpServletRequest req = createMockRequest("POST", "/abc/confirm-handover", null, null, testUser);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPost(req, resp);

        assertEquals(400, holder.status);
    }

    @Test
    @DisplayName("ExchangeServlet: POST with unknown action returns HTTP 404")
    void testExchangeServletPostUnknownAction() throws Exception {
        ExchangeServlet servlet = new ExchangeServlet();
        ResponseHolder holder = new ResponseHolder();
        HttpServletRequest req = createMockRequest("POST", "/1/unknown-action", null, null, testUser);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPost(req, resp);

        assertEquals(404, holder.status);
    }

    // =========================================================================
    // 4. ExchangeRequestServlet Tests
    // =========================================================================
    @Test
    @DisplayName("ExchangeRequestServlet: GET unauthenticated returns HTTP 401")
    void testExchangeRequestServletGetUnauthenticated() throws Exception {
        ExchangeRequestServlet servlet = new ExchangeRequestServlet();
        ResponseHolder holder = new ResponseHolder();
        HttpServletRequest req = createMockRequest("GET", null, null, null, null);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doGet(req, resp);

        assertEquals(401, holder.status);
    }

    @Test
    @DisplayName("ExchangeRequestServlet: GET with invalid ID returns HTTP 400")
    void testExchangeRequestServletGetInvalidId() throws Exception {
        ExchangeRequestServlet servlet = new ExchangeRequestServlet();
        ResponseHolder holder = new ResponseHolder();
        HttpServletRequest req = createMockRequest("GET", "/invalid-req-id", null, null, testUser);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doGet(req, resp);

        assertEquals(400, holder.status);
    }

    @Test
    @DisplayName("ExchangeRequestServlet: POST create request with missing bookId returns HTTP 400")
    void testExchangeRequestServletPostMissingBookId() throws Exception {
        ExchangeRequestServlet servlet = new ExchangeRequestServlet();
        ResponseHolder holder = new ResponseHolder();
        String body = "{\"exchangeMode\":\"SWAP\"}";
        HttpServletRequest req = createMockRequest("POST", null, null, body, testUser);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPost(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
        assertTrue(json.get("error").getAsString().contains("bookId is required"));
    }

    @Test
    @DisplayName("ExchangeRequestServlet: POST create request with invalid mode returns HTTP 400")
    void testExchangeRequestServletPostInvalidMode() throws Exception {
        ExchangeRequestServlet servlet = new ExchangeRequestServlet();
        ResponseHolder holder = new ResponseHolder();
        String body = "{\"bookId\":10,\"exchangeMode\":\"COMMERCIAL_RENT\"}";
        HttpServletRequest req = createMockRequest("POST", null, null, body, testUser);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPost(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
        assertTrue(json.get("error").getAsString().contains("Invalid sharing mode"));
    }

    // =========================================================================
    // 5. ProfileServlet Tests
    // =========================================================================
    @Test
    @DisplayName("ProfileServlet: GET unauthenticated returns HTTP 401")
    void testProfileServletGetUnauthenticated() throws Exception {
        ProfileServlet servlet = new ProfileServlet();
        ResponseHolder holder = new ResponseHolder();
        HttpServletRequest req = createMockRequest("GET", null, null, null, null);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doGet(req, resp);

        assertEquals(401, holder.status);
    }

    @Test
    @DisplayName("ProfileServlet: POST update unauthenticated returns HTTP 401")
    void testProfileServletPostUnauthenticated() throws Exception {
        ProfileServlet servlet = new ProfileServlet();
        ResponseHolder holder = new ResponseHolder();
        HttpServletRequest req = createMockRequest("POST", "/update", null, "{}", null);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPost(req, resp);

        assertEquals(401, holder.status);
    }

    @Test
    @DisplayName("ProfileServlet: POST password change with mismatched passwords returns HTTP 400")
    void testProfileServletPostPasswordMismatch() throws Exception {
        ProfileServlet servlet = new ProfileServlet();
        ResponseHolder holder = new ResponseHolder();
        String body = "{\"currentPassword\":\"oldpass123\",\"newPassword\":\"newpass123\",\"confirmPassword\":\"diffpass\"}";
        HttpServletRequest req = createMockRequest("POST", "/password", null, body, testUser);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPost(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertFalse(json.get("success").getAsBoolean());
        assertTrue(json.get("error").getAsString().contains("match"));
    }

    // =========================================================================
    // 6. UserServlet Tests
    // =========================================================================
    @Test
    @DisplayName("UserServlet: GET without ID parameter returns HTTP 400")
    void testUserServletGetWithoutId() throws Exception {
        UserServlet servlet = new UserServlet();
        ResponseHolder holder = new ResponseHolder();
        HttpServletRequest req = createMockRequest("GET", null, null, null, null);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doGet(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertTrue(json.has("error"));
        assertTrue(json.get("error").getAsString().contains("Parameter 'id' is required"));
    }

    @Test
    @DisplayName("UserServlet: GET with non-numeric ID returns HTTP 400")
    void testUserServletGetWithNonNumericId() throws Exception {
        UserServlet servlet = new UserServlet();
        ResponseHolder holder = new ResponseHolder();
        Map<String, String> params = Map.of("id", "xyz");
        HttpServletRequest req = createMockRequest("GET", null, params, null, null);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doGet(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertTrue(json.has("error"));
        assertTrue(json.get("error").getAsString().contains("Invalid numeric ID"));
    }

    @Test
    @DisplayName("UserServlet: POST with empty body returns HTTP 400")
    void testUserServletPostEmptyBody() throws Exception {
        UserServlet servlet = new UserServlet();
        ResponseHolder holder = new ResponseHolder();
        HttpServletRequest req = createMockRequest("POST", null, null, "", null);
        HttpServletResponse resp = createMockResponse(holder);

        servlet.doPost(req, resp);

        assertEquals(400, holder.status);
        JsonObject json = gson.fromJson(holder.out.toString(), JsonObject.class);
        assertTrue(json.has("error"));
    }
}
