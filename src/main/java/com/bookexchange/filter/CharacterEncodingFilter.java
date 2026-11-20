package com.bookexchange.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import java.io.IOException;

/**
 * ============================================================================
 * CharacterEncodingFilter
 * ============================================================================
 * Intercepts all incoming HTTP requests and forces UTF-8 encoding.
 * Ensures consistent handling of special characters, international names,
 * and book titles.
 * ============================================================================
 */
@WebFilter(filterName = "CharacterEncodingFilter", urlPatterns = "/*")
public class CharacterEncodingFilter implements Filter {

    private static final String ENCODING = "UTF-8";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization if needed
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding(ENCODING);
        response.setCharacterEncoding(ENCODING);

        if (response instanceof jakarta.servlet.http.HttpServletResponse httpResp && request instanceof jakarta.servlet.http.HttpServletRequest httpReq) {
            String path = httpReq.getRequestURI();
            if (path.endsWith(".html") || path.endsWith(".css") || path.endsWith(".js") || path.endsWith("/")) {
                httpResp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                httpResp.setHeader("Pragma", "no-cache");
                httpResp.setHeader("Expires", "0");
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // Cleanup if needed
    }
}
