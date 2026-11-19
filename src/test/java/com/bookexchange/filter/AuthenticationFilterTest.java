package com.bookexchange.filter;

import com.bookexchange.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================================
 * AuthenticationFilterTest - Security Filter Verification
 * ============================================================================
 * Tests:
 * 1. Static asset whitelisting (.css, .js, .svg, .png).
 * 2. Public endpoint access without authentication (/index.html, /login.html, /register.html, /api/auth/login).
 * 3. Protected endpoint recognition (/profile.html, /api/profile/*).
 * ============================================================================
 */
public class AuthenticationFilterTest {

    private final AuthenticationFilter filter = new AuthenticationFilter();

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

        // Public APIs
        assertTrue((Boolean) isPublicMethod.invoke(filter, "/api/health", "GET"));
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
}
