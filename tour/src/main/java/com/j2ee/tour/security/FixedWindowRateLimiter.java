package com.j2ee.tour.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FixedWindowRateLimiter {

    private static final int MAX_KEYS = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public FixedWindowRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * @return 0 when the request is allowed, otherwise the Retry-After seconds.
     */
    public long acquire(String key, int limit) {
        long minute = clock.instant().getEpochSecond() / 60;
        Window window = windows.compute(key, (ignored, current) ->
                current == null || current.minute() != minute
                        ? new Window(minute, 1)
                        : new Window(minute, current.count() + 1));

        if (windows.size() > MAX_KEYS) {
            windows.entrySet().removeIf(entry -> entry.getValue().minute() < minute);
        }

        if (window.count() <= limit) {
            return 0;
        }
        return 60 - (clock.instant().getEpochSecond() % 60);
    }

    private record Window(long minute, int count) {
    }
}
