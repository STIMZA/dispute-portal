package com.example.disputeportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application entry point.
 *
 * @SpringBootApplication switches on component scanning and auto-configuration. @ConfigurationPropertiesScan
 * registers the settings classes (DisputeProperties, AdminProperties, ApiProperties, LoginThrottleProperties) so their
 * values can be read from application*.yml and environment variables.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DisputePortalApplication {

    /**
     * Starts the embedded web server and the Spring application context.
     */
    public static void main(String[] args) {
        SpringApplication.run(DisputePortalApplication.class, args);
    }
}
