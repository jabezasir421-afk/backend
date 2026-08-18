package com.bluecollar.common.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitService {

    private final int maxRequests;
    private final long windowMs;
    private final Map<String, RequestWindow> windows = new ConcurrentHashMap<>();

    public RateLimitService(int maxRequests, long windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    public boolean allowRequest(String key) {
        RequestWindow window = windows.computeIfAbsent(key, ignored -> new RequestWindow());
        long now = Instant.now().toEpochMilli();
        window.prune(now);
        if (window.count >= maxRequests) {
            return false;
        }
        window.count++;
        return true;
    }

    private final class RequestWindow {
        private int count;
        private long startTime = Instant.now().toEpochMilli();

        private void prune(long now) {
            if (now - startTime >= windowMs) {
                count = 0;
                startTime = now;
            }
        }
    }
}
