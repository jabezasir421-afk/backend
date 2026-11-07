package com.bluecollar.common.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

public class RateLimitService {

    private static final long DEFAULT_MAX_CAPACITY = 100_000L;

    private final int maxRequests;
    private final long windowMs;
    private final long maxCapacity;
    private final Cache<String, RequestWindow> windows;

    public RateLimitService(int maxRequests, long windowMs) {
        this(maxRequests, windowMs, DEFAULT_MAX_CAPACITY);
    }

    public RateLimitService(int maxRequests, long windowMs, long maxCapacity) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
        this.maxCapacity = maxCapacity;
        this.windows = Caffeine.newBuilder()
                .maximumSize(maxCapacity)
                .expireAfterWrite(Duration.ofMillis(windowMs * 2))
                .build();
    }

    /**
     * Atomically checks and updates the rate limit window for the given client key.
     * Concurrency-safe: multiple threads competing on the same key will be executed atomically.
     *
     * @param key client identifier (e.g., IP address)
     * @return true if the request is permitted within limits, false if rate limited
     */
    public boolean allowRequest(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        long now = Instant.now().toEpochMilli();
        AtomicBoolean allowed = new AtomicBoolean(false);

        windows.asMap().compute(key, (k, window) -> {
            if (window == null || now - window.startTime >= windowMs) {
                allowed.set(true);
                return new RequestWindow(1, now);
            }

            if (window.count < maxRequests) {
                window.count++;
                allowed.set(true);
                return window;
            }

            allowed.set(false);
            return window;
        });

        return allowed.get();
    }

    /**
     * Explicitly cleans up expired entries to keep memory bounded.
     */
    public void cleanUp() {
        windows.cleanUp();
    }

    /**
     * Returns the current number of tracked keys in memory.
     */
    public long getTrackedKeyCount() {
        windows.cleanUp();
        return windows.estimatedSize();
    }

    /**
     * Resets rate limit for a specific key (useful for tests and administrative unblocking).
     */
    public void reset(String key) {
        windows.invalidate(key);
    }

    /**
     * Clears all tracked rate limit entries.
     */
    public void resetAll() {
        windows.invalidateAll();
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public long getWindowMs() {
        return windowMs;
    }

    public long getMaxCapacity() {
        return maxCapacity;
    }

    private static final class RequestWindow {
        private int count;
        private final long startTime;

        private RequestWindow(int count, long startTime) {
            this.count = count;
            this.startTime = startTime;
        }
    }
}
