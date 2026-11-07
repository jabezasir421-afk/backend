package com.bluecollar.notification.service;

import com.bluecollar.notification.config.NotificationProperties;
import com.bluecollar.notification.entity.EmailOutbox;
import com.bluecollar.notification.entity.OutboxStatus;
import com.bluecollar.notification.repository.EmailOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class EmailOutboxProcessor {

    private final EmailOutboxRepository emailOutboxRepository;
    private final JavaMailSender mailSender;
    private final NotificationProperties notificationProperties;
    private final TransactionTemplate transactionTemplate;
    private final String instanceId = UUID.randomUUID().toString();

    public EmailOutboxProcessor(
            EmailOutboxRepository emailOutboxRepository,
            JavaMailSender mailSender,
            NotificationProperties notificationProperties,
            PlatformTransactionManager transactionManager
    ) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.mailSender = mailSender;
        this.notificationProperties = notificationProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${bluecollar.notification.outbox.poll-interval-ms:30000}")
    public void processOutbox() {
        if (!notificationProperties.getEmail().isEnabled()) {
            return;
        }

        Instant staleThreshold = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<EmailOutbox> candidates = emailOutboxRepository.findClaimableEmails(
                OutboxStatus.PENDING,
                staleThreshold,
                PageRequest.of(0, 20)
        );

        for (EmailOutbox candidate : candidates) {
            processSingleOutboxMessage(candidate.getId(), staleThreshold);
        }
    }

    public boolean processSingleOutboxMessage(UUID outboxId, Instant staleThreshold) {
        // Step 1: Claim atomically in an isolated REQUIRES_NEW transaction
        boolean claimed = claimMessageAtomically(outboxId, staleThreshold);
        if (!claimed) {
            log.debug("Outbox message {} already claimed by another worker/instance", outboxId);
            return false;
        }

        // Fetch message details for sending
        EmailOutbox message = emailOutboxRepository.findById(outboxId).orElse(null);
        if (message == null || message.getStatus() == OutboxStatus.SENT) {
            // Idempotency check: avoid double send
            return false;
        }

        // Step 2: Send email via SMTP - OUTSIDE of database transaction!
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(notificationProperties.getEmail().getFrom());
            mailMessage.setTo(message.getRecipientEmail());
            mailMessage.setSubject(message.getSubject());
            mailMessage.setText(message.getBodyHtml().replaceAll("<[^>]+>", ""));
            mailSender.send(mailMessage);

            // Step 3: Record success in a separate isolated transaction
            recordSuccess(outboxId);
            return true;
        } catch (Exception ex) {
            // Step 4: Record failure in a separate isolated transaction
            recordFailure(outboxId, ex.getMessage());
            return false;
        }
    }

    public boolean claimMessageAtomically(UUID outboxId, Instant staleThreshold) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            int updated = emailOutboxRepository.claimMessage(
                    outboxId,
                    instanceId,
                    Instant.now(),
                    OutboxStatus.PENDING,
                    staleThreshold
            );
            return updated > 0;
        }));
    }

    public void recordSuccess(UUID outboxId) {
        transactionTemplate.executeWithoutResult(status -> {
            emailOutboxRepository.findById(outboxId).ifPresent(entry -> {
                entry.setStatus(OutboxStatus.SENT);
                entry.setSentAt(Instant.now());
                entry.setClaimedBy(null);
                entry.setClaimedAt(null);
                emailOutboxRepository.save(entry);
            });
        });
    }

    public void recordFailure(UUID outboxId, String errorMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            emailOutboxRepository.findById(outboxId).ifPresent(entry -> {
                short newRetryCount = (short) (entry.getRetryCount() + 1);
                entry.setRetryCount(newRetryCount);
                entry.setLastError(errorMessage);
                entry.setClaimedBy(null);
                entry.setClaimedAt(null);
                if (newRetryCount >= notificationProperties.getOutbox().getMaxRetries()) {
                    entry.setStatus(OutboxStatus.FAILED);
                } else {
                    entry.setStatus(OutboxStatus.PENDING);
                }
                emailOutboxRepository.save(entry);
                log.warn("Failed sending outbox email {}: {}", outboxId, errorMessage);
            });
        });
    }

    public boolean retryFailedEmail(UUID outboxId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            EmailOutbox entry = emailOutboxRepository.findById(outboxId)
                    .orElseThrow(() -> new IllegalArgumentException("Email outbox entry not found: " + outboxId));
            if (entry.getStatus() == OutboxStatus.SENT) {
                throw new IllegalStateException("Cannot retry already sent email");
            }
            entry.setStatus(OutboxStatus.PENDING);
            entry.setRetryCount((short) 0);
            entry.setLastError(null);
            entry.setClaimedBy(null);
            entry.setClaimedAt(null);
            emailOutboxRepository.save(entry);
            return true;
        }));
    }

    public String getInstanceId() {
        return instanceId;
    }
}
