package com.example.disputeportal.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Login page redirect after sign-in.
 */
@Controller
public class LoginController {

    /**
     * Shows the sign-in form. The POST is handled by Spring Security's form
     * login (see SecurityConfig).
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /**
     * The site root sends each user to their own start page: admins to the
     * dispute queue, customers to their transactions.
     */
    @GetMapping("/")
    public String home(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return admin ? "redirect:/admin/disputes" : "redirect:/transactions";
    }
}
