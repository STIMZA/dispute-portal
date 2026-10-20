package com.example.disputeportal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the machine-to-machine API.
 *
 * @param requireHttps  refuse API calls that did not arrive over HTTPS (true in the prod profile)
 * @param maxBodyBytes  largest request body accepted
 * @param loader        the client allowed to load transactions (scope transactions:load)
 * @param provisioner   the client allowed to create customer accounts (scope customers:create)
 */
@ConfigurationProperties(prefix = "app.api")
public record ApiProperties(
        @DefaultValue("false") boolean requireHttps,
        @DefaultValue("1048576") long maxBodyBytes,
        @DefaultValue Loader loader,
        @DefaultValue Provisioner provisioner) {

    /** @param key the API key (at least 32 characters). Empty means no key is created. */
    public record Loader(@DefaultValue("transaction-loader") String name, String key) {
    }

    /** @param key the API key (at least 32 characters, and different from the loader's). Empty means no key is created. */
    public record Provisioner(@DefaultValue("customer-provisioner") String name, String key) {
    }
}
