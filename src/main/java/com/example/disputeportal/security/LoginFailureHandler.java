package com.example.disputeportal.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/** Counts the failure, then shows the same generic error whether the username or the password was wrong. */
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginFailureHandler.class);

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
    private final LoginAttemptService loginAttempts;

    public LoginFailureHandler(LoginAttemptService loginAttempts) {
        this.loginAttempts = loginAttempts;
    }

    /**
     * Runs after a failed login.
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String key = LoginAttemptService.key(request.getRemoteAddr(), request.getParameter("username"));
        // Count this failure. recordFailure returns true when it tipped the client into a lockout.
        if (loginAttempts.recordFailure(key)) {
            log.warn("Too many failed logins; locking out client {}", key);
        }
        // Same message for an unknown user and a wrong password, so the page can't reveal which usernames exist.
        redirectStrategy.sendRedirect(request, response, "/login?error");
    }
}
