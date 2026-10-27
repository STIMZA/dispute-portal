package com.example.disputeportal.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application clock configuration, for consistent time handling.
 */
@Configuration
public class AppConfig {

    /**
     * All timestamps are created and stored in UTC, whatever the server's own
     * time zone is. Pages convert them to SAST for display (see DisplayTime).
     * Tests can replace this with a fixed clock.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
