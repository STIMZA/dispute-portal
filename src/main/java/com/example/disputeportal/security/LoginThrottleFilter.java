package com.example.disputeportal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stops login attempts from a locked-out client before the password is even checked. Deliberately not a
 * Spring bean: it is added to the security filter chain only, so it doesn't run twice.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottleFilter.class);

    private final LoginAttemptService loginAttempts;

    public LoginThrottleFilter(LoginAttemptService loginAttempts) {
        this.loginAttempts = loginAttempts;
    }

    /**
     * Only act on submissions of the login form (POST /login), not on page views or other URLs.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        boolean loginSubmit = "POST".equals(request.getMethod())
                && request.getRequestURI().equals(request.getContextPath() + "/login");
        return !loginSubmit;
    }

    /**
     * If this client is locked out, send it back to the login page with a message without checking the password.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = LoginAttemptService.key(request.getRemoteAddr(), request.getParameter("username"));
        if (loginAttempts.isBlocked(key)) {
            log.warn("Login attempt refused for locked-out client {}", key);
            response.sendRedirect(request.getContextPath() + "/login?locked");
            return;
        }
        chain.doFilter(request, response);
    }
}
