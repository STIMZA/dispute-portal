package com.example.disputeportal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One customer account to create. The account is always an ordinary customer: there is deliberately no way to
 * ask for an admin through the API.
 *
 * @param username the login name: lowercase letters, digits, '.', '_' and '-'
 * @param fullName the name shown to admins
 * @param password the initial password in plain text (it travels over HTTPS and is stored only as a BCrypt hash)
 */
public record CustomerLoadItem(
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "[a-z0-9._-]+", message = "may only contain lowercase letters, digits, '.', '_' and '-'")
        String username,

        @NotBlank @Size(max = 100)
        String fullName,

        // BCrypt only uses the first 72 bytes, so longer passwords are refused rather than silently cut short.
        @NotBlank @Size(min = 12, max = 72, message = "must be between 12 and 72 characters")
        String password) {

    /** The generated toString() would print the password; this one hides it, in case an object is ever logged. */
    @Override
    public String toString() {
        return "CustomerLoadItem[username=" + username + ", fullName=" + fullName + ", password=****]";
    }
}
