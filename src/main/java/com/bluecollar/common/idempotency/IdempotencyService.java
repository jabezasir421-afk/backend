package com.bluecollar.common.idempotency;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class IdempotencyService {

    private final Cache<String, CachedHttpResponse> cache;

    public IdempotencyService(
            @Value("${bluecollar.idempotency.ttl-hours:24}") int ttlHours,
            @Value("${bluecollar.idempotency.max-entries:50000}") int maxEntries
    ) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(ttlHours))
                .maximumSize(maxEntries)
                .build();
    }

    public Optional<CachedHttpResponse> get(String key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    public void put(String key, CachedHttpResponse response) {
        cache.put(key, response);
    }

    public void invalidate(String key) {
        cache.invalidate(key);
    }

    public void cleanUp() {
        cache.cleanUp();
    }

    public long size() {
        return cache.estimatedSize();
    }
}
