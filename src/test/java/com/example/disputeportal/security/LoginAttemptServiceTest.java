package com.example.disputeportal.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.disputeportal.config.LoginThrottleProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the lockout rules (5 failures in 15 minutes lock a client out for 15 minutes). A clock the test can move
 * forward stands in for real time.
 */
class LoginAttemptServiceTest {

    /** A clock the test can move forward. */
    static class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-13T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private TestClock clock;
    private LoginAttemptService service;
    private final String key = LoginAttemptService.key("10.0.0.1", "thandi");

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        service = new LoginAttemptService(new LoginThrottleProperties(5, 15, 15), clock);
    }

    @Test
    void locksOutOnTheFifthFailure() {
        for (int i = 0; i < 4; i++) {
            assertThat(service.recordFailure(key)).isFalse();
        }
        assertThat(service.isBlocked(key)).isFalse();

        assertThat(service.recordFailure(key)).isTrue();
        assertThat(service.isBlocked(key)).isTrue();
    }

    @Test
    void lockoutEndsAfterTheLockoutPeriod() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure(key);
        }
        clock.advance(Duration.ofMinutes(14));
        assertThat(service.isBlocked(key)).isTrue();

        clock.advance(Duration.ofMinutes(2));
        assertThat(service.isBlocked(key)).isFalse();
    }

    @Test
    void failuresOutsideTheWindowDoNotAccumulate() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure(key);
        }
        clock.advance(Duration.ofMinutes(16));

        assertThat(service.recordFailure(key)).isFalse();
        assertThat(service.isBlocked(key)).isFalse();
    }

    @Test
    void successClearsTheFailures() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure(key);
        }
        service.recordSuccess(key);

        assertThat(service.recordFailure(key)).isFalse();
    }

    @Test
    void differentClientsAndUsersAreTrackedSeparately() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure(key);
        }
        assertThat(service.isBlocked(key)).isTrue();
        assertThat(service.isBlocked(LoginAttemptService.key("10.0.0.2", "thandi"))).isFalse();
        assertThat(service.isBlocked(LoginAttemptService.key("10.0.0.1", "sipho"))).isFalse();
    }

    @Test
    void keysAreCaseInsensitiveAndSafeToLog() {
        assertThat(LoginAttemptService.key("10.0.0.1", "  THANDI ")).isEqualTo(key);
        assertThat(LoginAttemptService.key("10.0.0.1", "bad\nname")).doesNotContain("\n");
    }
}
