package com.portafolio.auth.service;

import com.portafolio.auth.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AttemptLimiterTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    final MutableClock clock = new MutableClock();
    final AttemptLimiter limiter = new AttemptLimiter(clock,
            new AppProperties(null, null, null, new AppProperties.RateLimit(3, 15, 3), null, null, null));

    @Test
    void permiteHastaElMaximoYLuegoBloqueaHastaQueVenceLaVentana() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.retryAfterSeconds("k", 3)).isZero();
            limiter.record("k");
        }
        assertThat(limiter.retryAfterSeconds("k", 3)).isEqualTo(15 * 60);

        clock.now = clock.now.plus(Duration.ofMinutes(10));
        assertThat(limiter.retryAfterSeconds("k", 3)).isEqualTo(5 * 60);

        clock.now = clock.now.plus(Duration.ofMinutes(5));
        assertThat(limiter.retryAfterSeconds("k", 3)).isZero();
    }

    @Test
    void resetYPurgaLimpianLasClaves() {
        limiter.record("a");
        limiter.record("a");
        limiter.record("a");
        limiter.reset("a");
        assertThat(limiter.retryAfterSeconds("a", 3)).isZero();

        limiter.record("b");
        clock.now = clock.now.plus(Duration.ofHours(1));
        limiter.purgeExpired();
        assertThat(limiter.retryAfterSeconds("b", 1)).isZero();
    }
}
