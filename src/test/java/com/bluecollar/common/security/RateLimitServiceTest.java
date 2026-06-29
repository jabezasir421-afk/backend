package com.bluecollar.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitServiceTest {

    @Test
    void allowsRequestsWithinLimit() {
        RateLimitService service = new RateLimitService(3, 60_000);

        assertTrue(service.allowRequest("127.0.0.1"));
        assertTrue(service.allowRequest("127.0.0.1"));
        assertTrue(service.allowRequest("127.0.0.1"));
    }

    @Test
    void blocksRequestsAfterLimitReached() {
        RateLimitService service = new RateLimitService(2, 60_000);

        assertTrue(service.allowRequest("127.0.0.1"));
        assertTrue(service.allowRequest("127.0.0.1"));
        assertFalse(service.allowRequest("127.0.0.1"));
    }
}
