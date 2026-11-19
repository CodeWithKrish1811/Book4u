package com.bookexchange.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.annotation.WebListener;

/**
 * ============================================================================
 * SecurityConfigListener - Application Security Lifecycle Listener
 * ============================================================================
 * Dynamically configures session cookie security flags based on runtime environment:
 * 1. HttpOnly: Enabled in all environments to prevent XSS session hijacking.
 * 2. SameSite=Lax: Configured to defend against Cross-Site Request Forgery (CSRF).
 * 3. Secure: Dynamically enabled in Production (Railway / Cloud HTTPS) while disabled
 *    in local development to maintain full compatibility with http://localhost:8080.
 * ============================================================================
 */
@WebListener
public class SecurityConfigListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            SessionCookieConfig cookieConfig = sce.getServletContext().getSessionCookieConfig();
            cookieConfig.setHttpOnly(true);

            // Configure SameSite=Lax to protect against Cross-Site Request Forgery (CSRF)
            try {
                cookieConfig.setAttribute("SameSite", "Lax");
            } catch (Exception e) {
                System.err.println("[SecurityConfig] Note on SameSite attribute configuration: " + e.getMessage());
            }

            boolean isProduction = DBConnection.isProductionEnvironment();
            if (isProduction) {
                cookieConfig.setSecure(true);
                System.out.println("[SecurityConfig] Production environment: Session cookies configured with HttpOnly=true, Secure=true, SameSite=Lax.");
            } else {
                cookieConfig.setSecure(false);
                System.out.println("[SecurityConfig] Local development environment: Session cookies configured with HttpOnly=true, Secure=false, SameSite=Lax (local HTTP compatible).");
            }
        } catch (Exception e) {
            System.err.println("[SecurityConfig] Note on session cookie initialization: " + e.getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
