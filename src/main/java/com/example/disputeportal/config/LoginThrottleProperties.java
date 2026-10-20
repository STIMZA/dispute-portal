package com.example.disputeportal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param maxAttempts    failed logins allowed within the window before the client is locked out
 * @param windowMinutes  how long failures are remembered
 * @param lockoutMinutes how long a locked-out client must wait
 */
@ConfigurationProperties(prefix = "app.security.login")
public record LoginThrottleProperties(
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("15") int windowMinutes,
        @DefaultValue("15") int lockoutMinutes) {
}
