package com.example.disputeportal.repository;

import com.example.disputeportal.entity.ApiClient;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for API clients (machine callers of the loading API).
 */
public interface ApiClientRepository extends JpaRepository<ApiClient, Long> {

    /**
     * Looks a client up by the SHA-256 hash of its key. This is how a presented key is authenticated.
     */
    Optional<ApiClient> findByKeyHash(String keyHash);

    /**
     * Looks a client up by name. Used when registering or rotating the configured key at startup.
     */
    Optional<ApiClient> findByName(String name);
}
