package com.example.disputeportal.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.disputeportal.entity.Customer;
import com.example.disputeportal.entity.Role;
import com.example.disputeportal.repository.CustomerRepository;

/**
 * This will enable the application to create the first admin account on
 * startup. Which you can use to log in and manage the disputes. You can
 * manipulate the admin account by changing the username and password in the
 * .env file. The password must be at least 12 characters long.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final CustomerRepository customers;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties properties;

    public AdminBootstrap(CustomerRepository customers, PasswordEncoder passwordEncoder, AdminProperties properties) {
        this.customers = customers;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    /**
     * Runs at startup. Creates the configured admin when enabled, and it
     * doesn't exist yet. Fails fast (the app refuses to start) when a password
     * is set but too short, rather than creating a weak admin or silently
     * ignoring it.
     */
    @Override
    @Transactional
    public void run(String... args) {
        if (!StringUtils.hasText(properties.password())) {
            log.info("No bootstrap admin configured (app.admin.password is empty)");
            return;
        }
        if (!StringUtils.hasText(properties.username())) {
            throw new IllegalStateException("app.admin.username must not be blank");
        }
        if (properties.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("app.admin.password (APP_ADMIN_PASSWORD / ADMIN_PASSWORD) is set but is only "
                    + properties.password().length() + " characters; it must be at least " + MIN_PASSWORD_LENGTH
                    + ". Use a longer password, or remove the setting to skip creating an admin. "
                    + "Check .env and your shell environment, and make sure the value has no stray quote characters.");
        }
        if (customers.findByUsername(properties.username()).isPresent()) {
            log.info("Admin account '{}' already exists; leaving it unchanged", properties.username());
            return;
        }
        customers.save(new Customer(properties.username(), passwordEncoder.encode(properties.password()),
                "Administrator", Role.ADMIN));
        log.info("Created admin account '{}'", properties.username());
    }
}
