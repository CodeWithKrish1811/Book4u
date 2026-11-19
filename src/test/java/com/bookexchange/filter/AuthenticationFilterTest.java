package com.bookexchange.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================================
 * AuthenticationFilterTest - Security Filter Verification
 * ============================================================================
 * Tests:
 * 1. Static asset whitelisting (.css, .js, .svg, .png).
 * 2. Public endpoint access without authentication (/index.html, /login.html, /register.html, /api/auth/login, /api/health/*).
 * 3. Protected endpoint recognition (/profile.html, /api/profile/*).
 * 4. CSRF Origin verification against cross-site attacks.
 * ============================================================================
 */
public class AuthenticationFilterTest {

    private final AuthenticationFilter filter = new AuthenticationFilter();

    private HttpServletRequest createMockRequestWithOrigin(String origin, String host) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getHeader".equals(name) && args.length == 1) {
                        String header = (String) args[0];
                        if ("Origin".equalsIgnoreCase(header)) {
                            return origin;
                        } else if ("Host".equalsIgnoreCase(header)) {
                            return host;
                        } else if ("X-Forwarded-Host".equalsIgnoreCase(header)) {
                            return null;
                        }
                    }
                    if ("getServerName".equals(name)) {
                        return host != null && host.contains(":") ? host.substring(0, host.indexOf(':')) : host;
                    }
                    return null;
                }
        );
    }

    @Test
    @DisplayName("Should recognize static web resources correctly")
    void testRecognizeStaticResources() throws Exception {
        Method isStaticMethod = AuthenticationFilter.class.getDeclaredMethod("isStaticResource", String.class);
        isStaticMethod.setAccessible(true);

        assertTrue((Boolean) isStaticMethod.invoke(filter, "/css/style.css"));
        assertTrue((Boolean) isStaticMethod.invoke(filter, "/js/auth.js"));
        assertTrue((Boolean) isStaticMethod.invoke(filter, "/js/app.js"));
        assertTrue((Boolean) isStaticMethod.invoke(filter, "/assets/logo.png"));
        assertTrue((Boolean) isStaticMethod.invoke(filter, "/favicon.ico"));

        assertFalse((Boolean) isStaticMethod.invoke(filter, "/profile.html"));
        assertFalse((Boolean) isStaticMethod.invoke(filter, "/api/profile"));
        assertFalse((Boolean) isStaticMethod.invoke(filter, "/login.html"));
    }

    @Test
    @DisplayName("Should classify public pages and public endpoints correctly")
    void testClassifyPublicEndpoints() throws Exception {
        Method isPublicMethod = AuthenticationFilter.class.getDeclaredMethod("isPublicResource", String.class, String.class);
        isPublicMethod.setAccessible(true);

        // Public pages
        assertTrue((Boolean) isPublicMethod.invoke(filter, "/index.html", "GET"));
        assertTrue((Boolean) isPublicMethod.invoke(filter, "/", "GET"));
        assertTrue((Boolean) isPublicMethod.invoke(filter, "/explore.html", "GET"));
        assertTrue((Boolean) isPublicMethod.invoke(filter, "/book-details.html", "GET"));
        assertTrue((Boolean) isPublicMethod.invoke(filter, "/login.html", "GET"));
        assertTrue((Boolean) isPublicMethod.invoke(filter, "/register.html", "GET"));

        // Public APIs including health diagnostics (full, live, ready)
        assertTrue((Boolean) isPublicMethod.invoke(filter, "/api/health", "GET"));
        assertTrue((Boolean) isPublicMethod.invoke(filter, "/api/health/live", "GET"));
        assertTrue((Boolean) isPublicMethod.invoke(filter, "/api/health/ready", "GET"));
        assertTrue((Boolean) isPublicMethod.invoke(filter, "/api/auth/login", "POST"));
        assertTrue((Boolean) isPublicMethod.invoke(filter, "/api/auth/register", "POST"));
        assertTrue((Boolean) isPublicMethod.invoke(filter, "/api/auth/status", "GET"));
        assertTrue((Boolean) isPublicMethod.invoke(filter, "/api/books", "GET"));

        // Protected resources: Must return false
        assertFalse((Boolean) isPublicMethod.invoke(filter, "/profile.html", "GET"));
        assertFalse((Boolean) isPublicMethod.invoke(filter, "/my-books.html", "GET"));
        assertFalse((Boolean) isPublicMethod.invoke(filter, "/add-book.html", "GET"));
        assertFalse((Boolean) isPublicMethod.invoke(filter, "/edit-book.html", "GET"));
        assertFalse((Boolean) isPublicMethod.invoke(filter, "/api/profile", "GET"));
        assertFalse((Boolean) isPublicMethod.invoke(filter, "/api/profile/update", "POST"));
        assertFalse((Boolean) isPublicMethod.invoke(filter, "/api/profile/password", "POST"));
        assertFalse((Boolean) isPublicMethod.invoke(filter, "/api/books", "POST")); // Creating a book listing requires authentication!
    }

    @Test
    @DisplayName("CSRF Origin Validation: allows same-origin and blocks malicious cross-site origins")
    void testCsrfOriginValidation() {
        // Direct request without Origin header (e.g. same-origin navigation, non-browser API client)
        HttpServletRequest reqNoOrigin = createMockRequestWithOrigin(null, "localhost:8080");
        assertTrue(filter.isAllowedOrigin(reqNoOrigin));

        // Same-origin localhost request
        HttpServletRequest reqLocalhost = createMockRequestWithOrigin("http://localhost:8080", "localhost:8080");
        assertTrue(filter.isAllowedOrigin(reqLocalhost));

        // Trusted Railway cloud domain request
        HttpServletRequest reqRailway = createMockRequestWithOrigin(
                "https://book4u-web-production.up.railway.app",
                "book4u-web-production.up.railway.app"
        );
        assertTrue(filter.isAllowedOrigin(reqRailway));

        // Malicious cross-site origin attempt
        HttpServletRequest reqMalicious = createMockRequestWithOrigin(
                "https://malicious-attacker.evil.com",
                "book4u-web-production.up.railway.app"
        );
        assertFalse(filter.isAllowedOrigin(reqMalicious), "Malicious origin must be rejected by CSRF protection");
    }
}
