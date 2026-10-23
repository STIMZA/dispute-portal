package com.example.disputeportal.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import com.example.disputeportal.config.LoginThrottleProperties;

/**
 * Tracks failed logins per client address and username, and locks that pair out
 * after too many failures.
 *
 * The state is held in memory, so it is per application instance and resets on
 * restart. With several instances, also rate-limit /login at the gateway or
 * load balancer.
 */
@Component
public class LoginAttemptService {

    private static final int PRUNE_THRESHOLD = 10_000;

    /**
     * The application remembers failures in the current window, when the window
     * started, and until when it is locked out.
     */
    private record State(int failures, Instant windowStart, Instant lockedUntil) {

    }

    /**
     * Keyed by client address + username. A concurrent map because many
     * requests run at the same time.
     */
    private final ConcurrentMap<String, State> attempts = new ConcurrentHashMap<>();
    private final LoginThrottleProperties properties;
    private final Clock clock;

    public LoginAttemptService(LoginThrottleProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Key = client address + username. Usernames are sanitised so they are safe
     * to log.
     */
    public static String key(String remoteAddress, String username) {
        String cleanUser = username == null ? "" : username.trim().toLowerCase();
        cleanUser = cleanUser.replaceAll("[^a-z0-9._@-]", "_");
        if (cleanUser.length() > 50) {
            cleanUser = cleanUser.substring(0, 50);
        }
        return remoteAddress + "|" + cleanUser;
    }

    /**
     * Is this client currently locked out?
     */
    public boolean isBlocked(String key) {
        State state = attempts.get(key);
        return state != null && state.lockedUntil() != null && state.lockedUntil().isAfter(clock.instant());
    }

    /**
     * @return true if this failure caused the client to be locked out
     */
    public boolean recordFailure(String key) {
        Instant now = clock.instant();
        // compute() is atomic per key, so two simultaneous failures can't be lost.
        State updated = attempts.compute(key, (k, state) -> {
            if (state == null || isExpired(state, now)) {
                return withFailure(new State(0, now, null), now);
            }
            return withFailure(state, now);
        });
        pruneIfLarge(now);
        return updated.lockedUntil() != null;
    }

    /**
     * A successful login or API call forgets earlier failures.
     */
    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    /**
     * Adds one failure, and starts a lockout once the allowed number is
     * reached.
     */
    private State withFailure(State state, Instant now) {
        int failures = state.failures() + 1;
        Instant lockedUntil = state.lockedUntil();
        if (failures >= properties.maxAttempts()) {
            lockedUntil = now.plus(Duration.ofMinutes(properties.lockoutMinutes()));
        }
        return new State(failures, state.windowStart(), lockedUntil);
    }

    /**
     * A locked-out entry expires when the lockout ends; otherwise it expires
     * when its counting window has passed.
     */
    private boolean isExpired(State state, Instant now) {
        if (state.lockedUntil() != null) {
            return !state.lockedUntil().isAfter(now);
        }
        return !state.windowStart().plus(Duration.ofMinutes(properties.windowMinutes())).isAfter(now);
    }

    /**
     * Housekeeping: drops expired entries once the map gets large, so it can't
     * grow without bound.
     */
    private void pruneIfLarge(Instant now) {
        if (attempts.size() > PRUNE_THRESHOLD) {
            attempts.entrySet().removeIf(e -> isExpired(e.getValue(), now));
        }
    }
}
