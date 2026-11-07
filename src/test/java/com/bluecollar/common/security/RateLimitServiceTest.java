package com.bluecollar.common.security;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void resetsAfterWindowExpires() throws InterruptedException {
        RateLimitService service = new RateLimitService(2, 50);

        assertTrue(service.allowRequest("127.0.0.1"));
        assertTrue(service.allowRequest("127.0.0.1"));
        assertFalse(service.allowRequest("127.0.0.1"));

        Thread.sleep(70);

        assertTrue(service.allowRequest("127.0.0.1"));
    }

    @Test
    void concurrentRequestsCannotBypassLimits() throws InterruptedException {
        int maxRequests = 10;
        int threadCount = 40;
        RateLimitService service = new RateLimitService(maxRequests, 60_000);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (service.allowRequest("concurrent-client-ip")) {
                        allowedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(maxRequests, allowedCount.get(), "Exactly maxRequests should be permitted under concurrency");
        assertEquals(threadCount - maxRequests, rejectedCount.get(), "Remaining concurrent requests must be rejected");
    }

    @Test
    void rateLimitMemoryRemainsBounded() {
        int capacity = 100;
        RateLimitService service = new RateLimitService(5, 60_000, capacity);

        // Insert 500 distinct keys
        for (int i = 0; i < 500; i++) {
            service.allowRequest("client-ip-" + i);
        }

        service.cleanUp();
        long trackedCount = service.getTrackedKeyCount();
        assertTrue(trackedCount <= capacity,
                "Tracked keys (" + trackedCount + ") must not exceed max capacity (" + capacity + ")");
    }
}
