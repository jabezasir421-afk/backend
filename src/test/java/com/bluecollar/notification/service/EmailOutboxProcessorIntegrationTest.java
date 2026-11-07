package com.bluecollar.notification.service;

import com.bluecollar.common.TestDbCleanup;
import com.bluecollar.notification.config.NotificationProperties;
import com.bluecollar.notification.entity.EmailOutbox;
import com.bluecollar.notification.entity.OutboxStatus;
import com.bluecollar.notification.repository.EmailOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class EmailOutboxProcessorIntegrationTest {

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private NotificationProperties notificationProperties;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    private TestDbCleanup testDbCleanup;

    private EmailOutboxProcessor processor1;
    private EmailOutboxProcessor processor2;

    @BeforeEach
    void setUp() {
        testDbCleanup.cleanCoreDomain();
        notificationProperties.getEmail().setEnabled(true);
        reset(mailSender);

        // Instantiate two separate processors representing two application instances
        processor1 = new EmailOutboxProcessor(emailOutboxRepository, mailSender, notificationProperties, transactionManager);
        processor2 = new EmailOutboxProcessor(emailOutboxRepository, mailSender, notificationProperties, transactionManager);
    }

    @Test
    void multipleInstancesDoNotSendSameOutboxMessageTwice() throws Exception {
        EmailOutbox outbox = emailOutboxRepository.saveAndFlush(EmailOutbox.builder()
                .recipientEmail("user@example.com")
                .subject("Booking Confirmation")
                .bodyHtml("<p>Your booking is confirmed.</p>")
                .status(OutboxStatus.PENDING)
                .build());

        // Simulate concurrent processing from both instances
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        Instant staleThreshold = Instant.now().minus(5, ChronoUnit.MINUTES);

        executor.submit(() -> {
            try {
                startLatch.await();
                if (processor1.processSingleOutboxMessage(outbox.getId(), staleThreshold)) {
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                // ignore
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                if (processor2.processSingleOutboxMessage(outbox.getId(), staleThreshold)) {
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                // ignore
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // Exactly one instance should succeed in claiming and sending
        assertEquals(1, successCount.get(), "Only one instance must process and send the email");
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));

        EmailOutbox processed = emailOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertEquals(OutboxStatus.SENT, processed.getStatus());
        assertNotNull(processed.getSentAt());
        assertNull(processed.getClaimedBy());
        assertNull(processed.getClaimedAt());
    }

    @Test
    void failedEmailCanBeRetriedSafely() {
        EmailOutbox outbox = emailOutboxRepository.saveAndFlush(EmailOutbox.builder()
                .recipientEmail("retry@example.com")
                .subject("Retry test")
                .bodyHtml("<p>Retry body</p>")
                .status(OutboxStatus.PENDING)
                .build());

        // 1. Configure mailSender to throw exception to simulate SMTP failure
        doThrow(new RuntimeException("SMTP Connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        Instant staleThreshold = Instant.now().minus(5, ChronoUnit.MINUTES);
        boolean result1 = processor1.processSingleOutboxMessage(outbox.getId(), staleThreshold);
        assertFalse(result1);

        EmailOutbox failedAttempt = emailOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertEquals(OutboxStatus.PENDING, failedAttempt.getStatus()); // Retry count < 3, so stays PENDING
        assertEquals((short) 1, failedAttempt.getRetryCount());
        assertEquals("SMTP Connection refused", failedAttempt.getLastError());
        assertNull(failedAttempt.getClaimedBy()); // Claim is cleared so it can be retried

        // 2. Fail again until max retries reached
        boolean result2 = processor1.processSingleOutboxMessage(outbox.getId(), staleThreshold);
        assertFalse(result2);
        boolean result3 = processor1.processSingleOutboxMessage(outbox.getId(), staleThreshold);
        assertFalse(result3);

        EmailOutbox exhausted = emailOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertEquals(OutboxStatus.FAILED, exhausted.getStatus());
        assertEquals((short) 3, exhausted.getRetryCount());

        // 3. Retry the failed email safely
        boolean resetSuccess = processor1.retryFailedEmail(exhausted.getId());
        assertTrue(resetSuccess);

        EmailOutbox resetOutbox = emailOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertEquals(OutboxStatus.PENDING, resetOutbox.getStatus());
        assertEquals((short) 0, resetOutbox.getRetryCount());
        assertNull(resetOutbox.getLastError());

        // 4. Now fix mailSender and verify it sends successfully
        reset(mailSender);
        boolean finalResult = processor1.processSingleOutboxMessage(resetOutbox.getId(), staleThreshold);
        assertTrue(finalResult);

        EmailOutbox finalOutbox = emailOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertEquals(OutboxStatus.SENT, finalOutbox.getStatus());
        assertNotNull(finalOutbox.getSentAt());
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void retryFailedEmailShouldRejectAlreadySentEmail() {
        EmailOutbox outbox = emailOutboxRepository.saveAndFlush(EmailOutbox.builder()
                .recipientEmail("sent@example.com")
                .subject("Already sent")
                .bodyHtml("<p>Content</p>")
                .status(OutboxStatus.SENT)
                .sentAt(Instant.now())
                .build());

        assertThrows(IllegalStateException.class, () -> processor1.retryFailedEmail(outbox.getId()));
    }

    @Test
    void staleLockCanBeReclaimedByAnotherInstance() {
        Instant oldClaimTime = Instant.now().minus(10, ChronoUnit.MINUTES);
        EmailOutbox outbox = emailOutboxRepository.saveAndFlush(EmailOutbox.builder()
                .recipientEmail("stale@example.com")
                .subject("Stale lock recovery")
                .bodyHtml("<p>Content</p>")
                .status(OutboxStatus.PENDING)
                .build());

        // Simulate an instance crashed and left claimed_by and claimed_at set 10 minutes ago
        outbox.setClaimedBy("crashed-instance");
        outbox.setClaimedAt(oldClaimTime);
        emailOutboxRepository.saveAndFlush(outbox);

        Instant staleThreshold = Instant.now().minus(5, ChronoUnit.MINUTES);

        // Instance 2 should successfully reclaim and send the message
        boolean result = processor2.processSingleOutboxMessage(outbox.getId(), staleThreshold);
        assertTrue(result);

        EmailOutbox sent = emailOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertEquals(OutboxStatus.SENT, sent.getStatus());
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }
}
