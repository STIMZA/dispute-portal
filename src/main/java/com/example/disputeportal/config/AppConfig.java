package com.example.disputeportal.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Small shared beans.
 */
@Configuration
public class AppConfig {

    /** All timestamps are created and stored in UTC; tests can replace this with a fixed clock. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
