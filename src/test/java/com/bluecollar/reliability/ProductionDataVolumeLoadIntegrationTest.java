package com.bluecollar.reliability;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.common.TestDbCleanup;
import com.bluecollar.skill.entity.Skill;
import com.bluecollar.skill.repository.SkillRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class ProductionDataVolumeLoadIntegrationTest {

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
    private SkillRepository skillRepository;

    @Autowired
    private TestDbCleanup testDbCleanup;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        testDbCleanup.cleanCoreDomain();
        skillRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // This load test is intentionally non-transactional because requests run on
        // worker threads. Remove its committed fixtures before the next test class.
        testDbCleanup.cleanCoreDomain();
        skillRepository.deleteAll();
    }

    @Test
    void testConcurrentSearchUnderRealisticDataVolume() throws InterruptedException {
        // 1. Seed realistic volume: multiple categories, skills, and 120 workers across 4 metropolitan cities
        Category plumbing = categoryRepository.saveAndFlush(Category.builder()
                .name("Plumbing")
                .description("Plumbing services")
                .active(true)
                .build());

        Category electrical = categoryRepository.saveAndFlush(Category.builder()
                .name("Electrical")
                .description("Electrical services")
                .active(true)
                .build());

        Category cleaning = categoryRepository.saveAndFlush(Category.builder()
                .name("Cleaning")
                .description("Home cleaning")
                .active(true)
                .build());

        Skill pipeFitting = skillRepository.saveAndFlush(Skill.builder()
                .name("Pipe Fitting")
                .description("Pipe fitting skill")
                .active(true)
                .build());

        Skill wiring = skillRepository.saveAndFlush(Skill.builder()
                .name("Wiring")
                .description("Electrical wiring skill")
                .active(true)
                .build());

        String[] cities = {"Mumbai", "Bengaluru", "Delhi", "Hyderabad"};
        int totalWorkers = 120;
        List<Worker> workers = new ArrayList<>(totalWorkers);

        for (int i = 0; i < totalWorkers; i++) {
            UserAccount account = userAccountRepository.saveAndFlush(UserAccount.builder()
                    .email("worker-vol-" + i + "@example.com")
                    .phoneNumber("+9198000" + String.format("%05d", i))
                    .passwordHash("hashed")
                    .role(UserRole.WORKER)
                    .active(true)
                    .emailVerified(true)
                    .build());

            Category cat = (i % 3 == 0) ? plumbing : (i % 3 == 1 ? electrical : cleaning);
            Skill sk = (i % 2 == 0) ? pipeFitting : wiring;
            String city = cities[i % cities.length];

            Worker worker = Worker.builder()
                    .userAccount(account)
                    .firstName("Worker" + i)
                    .lastName("Service")
                    .email(account.getEmail())
                    .phoneNumber(account.getPhoneNumber())
                    .category(cat)
                    .skills(Set.of(sk))
                    .primaryCity(city)
                    .hourlyRate(BigDecimal.valueOf(100.0 + (i % 50)))
                    .averageRating(BigDecimal.valueOf(3.5 + (i % 15) * 0.1))
                    .reviewCount(5 + (i % 50))
                    .verified(true)
                    .active(true)
                    .available(true)
                    .build();
            workers.add(worker);
        }
        workerRepository.saveAllAndFlush(workers);

        // 2. Execute concurrent load: 100 requests across 10 threads
        int totalRequests = 100;
        int threadPoolSize = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(totalRequests);

        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    long start = System.nanoTime();
                    String queryCity = cities[index % cities.length];

                    mockMvc.perform(get("/api/v1/search/workers")
                                    .param("city", queryCity)
                                    .param("page", "0")
                                    .param("size", "10"))
                            .andExpect(status().isOk());

                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    latencies.add(durationMs);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        // Release the gate
        startGate.countDown();
        boolean completed = doneGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Load test should complete within 15 seconds");
        assertEquals(totalRequests, successCount.get(), "All requests should succeed (0% error rate)");
        assertEquals(0, failureCount.get(), "Failure count must be 0");

        // Latency percentiles
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        long p50 = sorted.get((int) (sorted.size() * 0.50));
        long p95 = sorted.get((int) (sorted.size() * 0.95));
        double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0.0);

        System.out.printf("Load Test Results: Requests=%d, P50=%dms, P95=%dms, Avg=%.2fms%n",
                totalRequests, p50, p95, avg);

        assertTrue(p95 < 400, "P95 latency should remain under 400ms under load, was: " + p95 + "ms");
        assertTrue(avg < 150, "Average latency should remain under 150ms under load, was: " + avg + "ms");
    }
}
