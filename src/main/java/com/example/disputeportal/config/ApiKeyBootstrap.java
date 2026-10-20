package com.example.disputeportal.config;

import com.example.disputeportal.security.ApiScopes;
import com.example.disputeportal.service.ApiClientService;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Registers the API clients from configuration on startup: the loader (may load transactions) and the
 * provisioner (may create customers). The configured key is the source of truth: changing it rotates the
 * stored hash, and the old key stops working immediately.
 */
@Component
public class ApiKeyBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyBootstrap.class);

    private final ApiProperties properties;
    private final ApiClientService apiClients;

    public ApiKeyBootstrap(ApiProperties properties, ApiClientService apiClients) {
        this.properties = properties;
        this.apiClients = apiClients;
    }

    /**
     * Runs at startup. For each client with a key configured, registers (or rotates) it. Fails fast when a key is
     * set but too short, or when both clients were given the same key; a client with no key simply can't
     * authenticate, so its endpoint is unusable.
     */
    @Override
    public void run(String... args) {
        String loaderKey = properties.loader().key();
        String provisionerKey = properties.provisioner().key();
        if (StringUtils.hasText(loaderKey) && loaderKey.equals(provisionerKey)) {
            // One key must never grant both permissions: that would defeat having separate scopes.
            throw new IllegalStateException("The loader and provisioner API keys must be different, "
                    + "because each key grants a different permission");
        }
        register("loader", "app.api.loader.key (APP_API_LOADER_KEY)",
                properties.loader().name(), loaderKey, ApiScopes.TRANSACTIONS_LOAD);
        register("provisioner", "app.api.provisioner.key (APP_API_PROVISIONER_KEY)",
                properties.provisioner().name(), provisionerKey, ApiScopes.CUSTOMERS_CREATE);
    }

    private void register(String label, String setting, String clientName, String key, String scope) {
        if (!StringUtils.hasText(key)) {
            log.info("No {} API key configured ({} is empty); its endpoint is unusable", label, setting);
            return;
        }
        if (key.length() < ApiClientService.MIN_KEY_LENGTH) {
            // Only the length is reported, never the key itself.
            throw new IllegalStateException(setting + " is set but is only " + key.length()
                    + " characters; it must be at least " + ApiClientService.MIN_KEY_LENGTH
                    + ". Generate one with: openssl rand -base64 48");
        }
        apiClients.registerOrRotate(clientName, key, Set.of(scope));
    }
}
