package com.example.disputeportal.config;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.disputeportal.security.ApiScopes;
import com.example.disputeportal.service.ApiClientService;

/**
 * Creates the API clients using the configured keys. The loader key can be used
 * for loading transactions, and the provisioner key can be used for creating
 * customers.
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
     * When the application starts up, this method is called. For each client
     * with the key configured in the properties, it will register the client
     * with the key and scope. It will throw an exception if a key is set but
     * too short, or when both clients were given the same key. This will
     * prevent clients without a key from accessing the API.
     */
    @Override
    public void run(String... args) {
        String loaderKey = properties.loader().key();
        String provisionerKey = properties.provisioner().key();
        if (StringUtils.hasText(loaderKey) && loaderKey.equals(provisionerKey)) {
            // To manage the scopes separately, the loader and provisioner keys must be different.
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
            // Avoid logging the key itself, because it is a secret.
            throw new IllegalStateException(setting + " is set but is only " + key.length()
                    + " characters; it must be at least " + ApiClientService.MIN_KEY_LENGTH
                    + ". Generate one with: openssl rand -base64 48");
        }
        apiClients.registerOrRotate(clientName, key, Set.of(scope));
    }
}
