package com.bookexchange.filter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * ============================================================================
 * AuthenticationFilter - Security Gatekeeper
 * ============================================================================
 * University Academic Project: Student Peer-to-Peer Book Exchange System
 *
 * Responsibilities:
 * 1. Protects private pages (/profile.html, /dashboard.html) from unauthenticated access.
 * 2. Protects sensitive API endpoints (/api/profile/*, POST /api/books) with HTTP 401.
 * 3. Whitelists public resources (CSS, JS, images, health checks, login/register).
 * 4. Prevents authenticated students from unnecessarily visiting /login.html or /register.html
 *    by smoothly redirecting them to /profile.html.
 * ============================================================================
 */
@WebFilter(filterName = "AuthenticationFilter", urlPatterns = "/*")
public class AuthenticationFilter implements Filter {

    private final Gson gson = new Gson();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("[AuthenticationFilter] Initialized successfully.");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String contextPath = req.getContextPath();
        String uri = req.getRequestURI();
        String path = uri.substring(contextPath.length());

        // Check if user has an active authenticated session
        HttpSession session = req.getSession(false);
        boolean isAuthenticated = (session != null && session.getAttribute("currentUser") != null);

        // 1. Whitelist static assets
        if (isStaticResource(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 2. Prevent already logged-in users from accessing login or registration pages
        if (isAuthenticated && (path.equals("/login.html") || path.equals("/register.html"))) {
            resp.sendRedirect(contextPath + "/profile.html");
            return;
        }

        // 3. Whitelist public pages and endpoints
        if (isPublicResource(path, req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // 4. If request is for a protected resource and user is NOT authenticated:
        if (!isAuthenticated) {
            if (path.startsWith("/api/")) {
                // For API requests, return structured HTTP 401 JSON
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");

                JsonObject err = new JsonObject();
                err.addProperty("success", false);
                err.addProperty("error", "Authentication required. Please log in first.");
                try (PrintWriter out = resp.getWriter()) {
                    out.print(gson.toJson(err));
                }
                return;
            } else {
                // For web page requests, redirect to login page with alert parameter
                resp.sendRedirect(contextPath + "/login.html?authRequired=true");
                return;
            }
        }

        // 5. Authenticated user accessing protected resource: proceed
        chain.doFilter(request, response);
    }

    /**
     * Determines if a URI points to static styling, script, or image assets.
     */
    private boolean isStaticResource(String path) {
        return path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/assets/") ||
               path.startsWith("/images/") ||
               path.endsWith(".css") ||
               path.endsWith(".js") ||
               path.endsWith(".ico") ||
               path.endsWith(".png") ||
               path.endsWith(".jpg") ||
               path.endsWith(".svg");
    }

    /**
     * Determines if an endpoint or page is open to the public / unauthenticated users.
     */
    private boolean isPublicResource(String path, String method) {
        // Welcome and public catalog pages
        if (path.equals("") || path.equals("/") || path.equals("/index.html") ||
            path.equals("/login.html") || path.equals("/register.html")) {
            return true;
        }

        // Public Health Diagnostics
        if (path.startsWith("/api/health")) {
            return true;
        }

        // Public Authentication Endpoints
        if (path.startsWith("/api/auth/login") ||
            path.startsWith("/api/auth/register") ||
            path.startsWith("/api/auth/status") ||
            path.startsWith("/api/auth/logout")) {
            return true;
        }

        // Public Book Catalog Browsing (GET is public, POST requires login)
        if (path.startsWith("/api/books") && "GET".equalsIgnoreCase(method)) {
            return true;
        }

        return false;
    }

    @Override
    public void destroy() {
        // Cleanup if required
    }
}
