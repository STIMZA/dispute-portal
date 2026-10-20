package com.example.disputeportal.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/** Sends admins to the dispute queue and customers to their transactions after login. */
public class RoleBasedSuccessHandler implements AuthenticationSuccessHandler {

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
    private final LoginAttemptService loginAttempts;

    public RoleBasedSuccessHandler(LoginAttemptService loginAttempts) {
        this.loginAttempts = loginAttempts;
    }

    /**
     * Runs after a successful login: clears earlier failures for this client and redirects to the right start page.
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        // A successful login wipes this client's earlier failed attempts.
        loginAttempts.recordSuccess(LoginAttemptService.key(request.getRemoteAddr(), request.getParameter("username")));
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        // Admins start on the dispute queue; customers on their transactions.
        redirectStrategy.sendRedirect(request, response, admin ? "/admin/disputes" : "/transactions");
    }
}
