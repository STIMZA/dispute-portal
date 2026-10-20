package com.example.disputeportal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bootstrap admin account. Supplied through the environment (APP_ADMIN_USERNAME / APP_ADMIN_PASSWORD).
 * If the password is empty, no admin is created.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(@DefaultValue("admin") String username, String password) {
}
