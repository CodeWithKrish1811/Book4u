package com.bookexchange.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * ============================================================================
 * LoggingFilter
 * ============================================================================
 * Demonstrates: Servlet Filter lifecycle, interceptor pattern, HTTP request monitoring.
 * Logs method, request URI, client IP address, and execution duration.
 * ============================================================================
 */
@WebFilter(filterName = "LoggingFilter", urlPatterns = "/*")
public class LoggingFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("[LoggingFilter] Initialized request logging filter.");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        long startTime = System.currentTimeMillis();
        String method = httpRequest.getMethod();
        String uri = httpRequest.getRequestURI();

        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = httpResponse.getStatus();
            System.out.printf("[HTTP] %s %s -> %d (%d ms)%n", method, uri, status, duration);
        }
    }

    @Override
    public void destroy() {
        System.out.println("[LoggingFilter] Destroyed logging filter.");
    }
}
