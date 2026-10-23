package com.example.disputeportal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.disputeportal.entity.ApiClient;
import com.example.disputeportal.repository.ApiClientRepository;

/**
 * Looks up API clients by the SHA-256 hash of their key. A fast hash is
 * appropriate here because API keys are long random values (not human-chosen
 * passwords), and the lookup is by hash so no secret is compared.
 */
@Service
public class ApiClientService {

    public static final int MIN_KEY_LENGTH = 32;
    private static final int MAX_KEY_LENGTH = 256;
    private static final Duration USED_MARK_INTERVAL = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(ApiClientService.class);

    private final ApiClientRepository repository;
    private final Clock clock;

    public ApiClientService(ApiClientRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Checks a presented API key.
     *
     * @return the client (name and permitted scopes) if the key belongs to an
     * enabled, unexpired client
     */
    @Transactional
    public Optional<AuthenticatedClient> authenticate(String presentedKey) {
        if (presentedKey == null || presentedKey.length() < MIN_KEY_LENGTH || presentedKey.length() > MAX_KEY_LENGTH) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return repository.findByKeyHash(hash(presentedKey))
                .filter(client -> client.isUsableAt(now))
                .map(client -> {
                    client.markUsed(now, USED_MARK_INTERVAL);
                    return new AuthenticatedClient(client.getName(), client.getScopes());
                });
    }

    /**
     * Called at startup with the configured key. The configuration is the
     * source of truth: - a new client is created. - a different key rotates the
     * stored hash (the old key stops working at once). - different scopes
     * replace the stored scopes.
     *
     * @param scopes what this key may do, e.g. {"transactions:load"}
     */
    @Transactional
    public void registerOrRotate(String name, String key, Set<String> scopes) {
        String keyHash = hash(key);
        String scopeText = String.join(",", new TreeSet<>(scopes));
        Optional<ApiClient> existing = repository.findByName(name);
        if (existing.isEmpty()) {
            repository.save(new ApiClient(name, keyHash, scopeText, LocalDateTime.now(clock)));
            log.info("Registered API client '{}' with scopes {}", name, scopeText);
            return;
        }
        ApiClient client = existing.get();
        if (!client.getKeyHash().equals(keyHash)) {
            client.replaceKeyHash(keyHash);
            log.info("Rotated the key of API client '{}'; the previous key no longer works", name);
        }
        if (!client.getScopes().equals(new TreeSet<>(scopes))) {
            client.replaceScopes(scopeText);
            log.info("Updated the scopes of API client '{}' to {}", name, scopeText);
        }
    }

    /**
     * SHA-256 of the key as lowercase hex (64 characters). The same function is
     * used to store a key and to look one up, so the plain key never needs to
     * be stored.
     */
    public static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
