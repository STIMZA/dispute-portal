package com.example.disputeportal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.disputeportal.entity.ApiClient;
import com.example.disputeportal.repository.ApiClientRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for API key authentication: hashing, unknown/disabled/expired clients and key length checks.
 */
@ExtendWith(MockitoExtension.class)
class ApiClientServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC);
    private static final String KEY = "k".repeat(40);

    @Mock
    private ApiClientRepository repository;

    private ApiClientService service;

    @BeforeEach
    void setUp() {
        service = new ApiClientService(repository, CLOCK);
    }

    @Test
    void validKeyIdentifiesTheClient() {
        when(repository.findByKeyHash(ApiClientService.hash(KEY)))
                .thenReturn(Optional.of(new ApiClient("loader", ApiClientService.hash(KEY), "transactions:load", LocalDateTime.now(CLOCK))));

        Optional<AuthenticatedClient> client = service.authenticate(KEY);

        assertThat(client).isPresent();
        assertThat(client.get().name()).isEqualTo("loader");
        // The scopes travel with the client, so the access rules can tell what the key may do.
        assertThat(client.get().scopes()).containsExactly("transactions:load");
    }

    @Test
    void severalScopesAreReadBackAsASet() {
        ApiClient both = new ApiClient("both", ApiClientService.hash(KEY), "customers:create, transactions:load",
                LocalDateTime.now(CLOCK));

        assertThat(both.getScopes()).containsExactlyInAnyOrder("customers:create", "transactions:load");
    }

    @Test
    void unknownKeyIsRejected() {
        when(repository.findByKeyHash(ApiClientService.hash(KEY))).thenReturn(Optional.empty());

        assertThat(service.authenticate(KEY)).isEmpty();
    }

    @Test
    void disabledOrExpiredClientsAreRejected() {
        ApiClient disabled = new ApiClient("loader", ApiClientService.hash(KEY), "transactions:load", LocalDateTime.now(CLOCK));
        ReflectionTestUtils.setField(disabled, "enabled", false);
        when(repository.findByKeyHash(ApiClientService.hash(KEY))).thenReturn(Optional.of(disabled));
        assertThat(service.authenticate(KEY)).isEmpty();

        ApiClient expired = new ApiClient("loader", ApiClientService.hash(KEY), "transactions:load", LocalDateTime.now(CLOCK));
        ReflectionTestUtils.setField(expired, "expiresAt", LocalDateTime.now(CLOCK).minusMinutes(1));
        when(repository.findByKeyHash(ApiClientService.hash(KEY))).thenReturn(Optional.of(expired));
        assertThat(service.authenticate(KEY)).isEmpty();
    }

    @Test
    void keysOutsideTheAllowedLengthAreRejectedWithoutALookup() {
        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("short")).isEmpty();
        assertThat(service.authenticate("x".repeat(300))).isEmpty();
    }

    @Test
    void hashIsStableSha256Hex() {
        assertThat(ApiClientService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
