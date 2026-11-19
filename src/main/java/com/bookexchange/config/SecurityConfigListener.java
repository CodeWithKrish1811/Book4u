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
 * 1. Production (Railway / Cloud HTTPS): Enables HttpOnly and Secure flags to prevent
 *    XSS session theft and transmission over unencrypted channels.
 * 2. Local Development (Localhost HTTP): Enables HttpOnly while keeping Secure=false so
 *    sessions function properly on plain http://localhost:8080.
 * ============================================================================
 */
@WebListener
public class SecurityConfigListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            SessionCookieConfig cookieConfig = sce.getServletContext().getSessionCookieConfig();
            cookieConfig.setHttpOnly(true);

            boolean isProduction = DBConnection.isProductionEnvironment();
            if (isProduction) {
                cookieConfig.setSecure(true);
                System.out.println("[SecurityConfig] Production environment detected: Session cookies configured with HttpOnly=true, Secure=true.");
            } else {
                cookieConfig.setSecure(false);
                System.out.println("[SecurityConfig] Local development environment: Session cookies configured with HttpOnly=true, Secure=false (local HTTP compatible).");
            }
        } catch (Exception e) {
            System.err.println("[SecurityConfig] Note on session cookie initialization: " + e.getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
