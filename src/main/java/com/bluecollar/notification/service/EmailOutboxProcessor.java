package com.bluecollar.notification.service;

import com.bluecollar.notification.config.NotificationProperties;
import com.bluecollar.notification.entity.EmailOutbox;
import com.bluecollar.notification.entity.OutboxStatus;
import com.bluecollar.notification.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "bluecollar.notification.email",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class EmailOutboxProcessor {

    private final EmailOutboxRepository emailOutboxRepository;
    private final JavaMailSender mailSender;
    private final NotificationProperties notificationProperties;

    @Scheduled(fixedDelayString = "${bluecollar.notification.outbox.poll-interval-ms:30000}")
    @Transactional
    public void processOutbox() {
        if (!notificationProperties.getEmail().isEnabled()) {
            return;
        }
        List<EmailOutbox> pending = emailOutboxRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        for (EmailOutbox entry : pending) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(notificationProperties.getEmail().getFrom());
                message.setTo(entry.getRecipientEmail());
                message.setSubject(entry.getSubject());
                message.setText(entry.getBodyHtml().replaceAll("<[^>]+>", ""));
                mailSender.send(message);
                entry.setStatus(OutboxStatus.SENT);
                entry.setSentAt(Instant.now());
            } catch (Exception exception) {
                entry.setRetryCount((short) (entry.getRetryCount() + 1));
                entry.setLastError(exception.getMessage());
                if (entry.getRetryCount() >= notificationProperties.getOutbox().getMaxRetries()) {
                    entry.setStatus(OutboxStatus.FAILED);
                }
                log.warn("Failed to send email outbox entry {}: {}", entry.getId(), exception.getMessage());
            }
            emailOutboxRepository.save(entry);
        }
    }
}
