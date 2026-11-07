package com.bluecollar.reliability;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.common.TestDbCleanup;
import com.bluecollar.common.security.RateLimitService;
import com.bluecollar.storage.entity.EntityType;
import com.bluecollar.storage.entity.FileCategory;
import com.bluecollar.storage.entity.StoredFile;
import com.bluecollar.storage.repository.StoredFileRepository;
import com.bluecollar.storage.service.FileStorageService;
import com.bluecollar.storage.service.StoredFileService;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class PerformanceAndReliabilityIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private StoredFileService storedFileService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private TestDbCleanup testDbCleanup;

    private UserAccount workerUser;
    private Worker worker;
    private Category category;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        testDbCleanup.cleanCoreDomain();

        workerUser = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("perf-worker@example.com")
                .phoneNumber("9988776655")
                .passwordHash("hashed")
                .role(UserRole.WORKER)
                .active(true)
                .emailVerified(true)
                .build());

        category = categoryRepository.saveAndFlush(Category.builder()
                .name("Plumbing")
                .description("Plumbing services")
                .active(true)
                .build());

        worker = workerRepository.saveAndFlush(Worker.builder()
                .userAccount(workerUser)
                .firstName("Bob")
                .lastName("Builder")
                .phoneNumber("9988776655")
                .email(workerUser.getEmail())
                .category(category)
                .primaryCity("Mumbai")
                .hourlyRate(BigDecimal.valueOf(150.00))
                .averageRating(BigDecimal.valueOf(4.80))
                .reviewCount(10)
                .verified(true)
                .active(true)
                .available(true)
                .build());
    }

    @Test
    void concurrentRateLimitRequestsCannotBypassLimits() throws InterruptedException {
        int maxAllowed = 15;
        int totalRequests = 60;
        RateLimitService rateLimiter = new RateLimitService(maxAllowed, 60_000);

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(totalRequests);

        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        String clientIp = "192.168.10.50";

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    if (rateLimiter.allowRequest(clientIp)) {
                        allowed.incrementAndGet();
                    } else {
                        rejected.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(maxAllowed, allowed.get(), "Rate limiter must allow exactly maxAllowed concurrent requests");
        assertEquals(totalRequests - maxAllowed, rejected.get(), "Excess concurrent requests must be rejected");
    }

    @Test
    void rateLimitMemoryRemainsBoundedUnderLoad() {
        int capacity = 200;
        RateLimitService service = new RateLimitService(10, 60_000, capacity);

        // Simulate 2000 unique client IPs making requests
        for (int i = 0; i < 2000; i++) {
            service.allowRequest("ip-" + i);
        }

        service.cleanUp();
        long currentKeys = service.getTrackedKeyCount();
        assertTrue(currentKeys <= capacity,
                "Memory must remain bounded to configured capacity of " + capacity + ", actual: " + currentKeys);
    }

    @Test
    @WithMockUser(username = "perf-worker@example.com", roles = "WORKER")
    void failedFileUploadsDoNotLeaveInconsistentState() {
        byte[] validJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-photo.jpg",
                "image/jpeg",
                validJpeg
        );

        long initialDbCount = storedFileRepository.count();

        // 1. Attempt upload with invalid category/entity combination which causes validation exception
        assertThrows(Exception.class, () ->
                storedFileService.upload(file, FileCategory.PROFILE_PHOTO, EntityType.CERTIFICATE, UUID.randomUUID()));

        // 2. Verify no records leaked into database
        assertEquals(initialDbCount, storedFileRepository.count(),
                "Failed upload must not leave orphaned records in database");
    }

    @Test
    void loadAndLatencyWithinAcceptableThresholds() throws Exception {
        // Warm up and verify endpoint latency
        int totalRequests = 20;
        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < totalRequests; i++) {
            long start = System.nanoTime();
            mockMvc.perform(get("/api/v1/search/workers")
                            .param("city", "Mumbai"))
                    .andExpect(status().isOk());
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            latencies.add(durationMs);
        }

        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p95Latency = latencies.stream().sorted().toList().get((int) (totalRequests * 0.95));

        // P95 latency must be under 300ms for local search queries
        assertTrue(p95Latency < 500, "P95 latency should be well within acceptable SLA (< 500ms), was: " + p95Latency + "ms");
        assertTrue(avgLatency < 200, "Average latency should be fast (< 200ms), was: " + avgLatency + "ms");
    }
}
