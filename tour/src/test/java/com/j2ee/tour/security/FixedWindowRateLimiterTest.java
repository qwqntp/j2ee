package com.j2ee.tour.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-10-26T00:00:30Z");

    @Test
    void allowsUpToLimitThenRejectsWithRetryAfter() {
        AtomicReference<Instant> now = new AtomicReference<>(NOW);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(clockAt(now));

        for (int i = 0; i < 10; i++) {
            assertThat(limiter.acquire("ip|login", 10)).isZero();
        }

        assertThat(limiter.acquire("ip|login", 10)).isBetween(1L, 60L);
    }

    @Test
    void resetsInTheNextMinute() {
        AtomicReference<Instant> now = new AtomicReference<>(NOW);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(clockAt(now));

        for (int i = 0; i < 10; i++) {
            limiter.acquire("ip|login", 10);
        }
        assertThat(limiter.acquire("ip|login", 10)).isPositive();

        now.set(NOW.plusSeconds(31));
        assertThat(limiter.acquire("ip|login", 10)).isZero();
    }

    @Test
    void keepsSeparateBucketsPerKey() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(Clock.fixed(NOW, ZoneOffset.UTC));

        for (int i = 0; i < 10; i++) {
            limiter.acquire("ip1|login", 10);
        }

        assertThat(limiter.acquire("ip1|login", 10)).isPositive();
        assertThat(limiter.acquire("ip2|login", 10)).isZero();
    }

    private Clock clockAt(AtomicReference<Instant> now) {
        return new Clock() {
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
                return now.get();
            }
        };
    }
}
