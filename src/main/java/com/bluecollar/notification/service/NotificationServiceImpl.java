package com.bluecollar.notification.service;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.common.exception.UnauthorizedException;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.notification.config.NotificationProperties;
import com.bluecollar.notification.dto.NotificationPreferenceResponse;
import com.bluecollar.notification.dto.NotificationResponse;
import com.bluecollar.notification.dto.UpdateNotificationPreferenceRequest;
import com.bluecollar.notification.entity.*;
import com.bluecollar.notification.exception.NotificationNotFoundException;
import com.bluecollar.notification.repository.EmailOutboxRepository;
import com.bluecollar.notification.repository.NotificationPreferenceRepository;
import com.bluecollar.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final UserAccountRepository userAccountRepository;
    private final NotificationProperties notificationProperties;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyUser(
            UUID recipientUserId,
            NotificationType type,
            NotificationChannel channel,
            String title,
            String body,
            String referenceType,
            UUID referenceId
    ) {
        NotificationPreference preference = findPreferenceOrDefault(recipientUserId);

        boolean inAppRequested = (channel == NotificationChannel.IN_APP || channel == NotificationChannel.BOTH);
        boolean emailRequested = (channel == NotificationChannel.EMAIL || channel == NotificationChannel.BOTH);

        boolean inAppEnabled = inAppRequested && Boolean.TRUE.equals(preference.getInAppEnabled());
        boolean emailEnabled = emailRequested && shouldSendEmail(type, preference);

        Notification savedNotification = null;
        if (inAppEnabled) {
            Notification notification = Notification.builder()
                    .recipientUserId(recipientUserId)
                    .type(type)
                    .channel(channel)
                    .title(title)
                    .body(body)
                    .referenceType(referenceType)
                    .referenceId(referenceId)
                    .build();
            savedNotification = notificationRepository.save(notification);
            publishRealtimeNotification(savedNotification);
        }

        if (emailEnabled) {
            final Notification notifForEmail = savedNotification;
            userAccountRepository.findById(recipientUserId).ifPresent(user -> {
                if (user.getEmail() != null && !user.getEmail().isBlank()) {
                    queueEmail(notifForEmail, user.getEmail(), title, body);
                }
            });
        }
    }

    @Override
    public void sendEmail(String recipientEmail, String subject, String bodyHtml) {
        EmailOutbox outbox = EmailOutbox.builder()
                .recipientEmail(recipientEmail)
                .subject(subject)
                .bodyHtml(bodyHtml)
                .status(OutboxStatus.PENDING)
                .build();
        emailOutboxRepository.save(outbox);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(Pageable pageable) {
        UUID userId = SecurityUtils.getCurrentUser().userAccountId();
        return notificationRepository.findByRecipientUserIdAndActiveTrueOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount() {
        return notificationRepository.countByRecipientUserIdAndActiveTrueAndReadAtIsNull(
                SecurityUtils.getCurrentUser().userAccountId()
        );
    }

    @Override
    public void markAsRead(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        assertOwner(notification);
        notification.setReadAt(Instant.now());
        notificationRepository.save(notification);
    }

    @Override
    public void markAllAsRead() {
        notificationRepository.markAllRead(SecurityUtils.getCurrentUser().userAccountId(), Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getPreferences() {
        return toPreferenceResponse(findPreferenceOrDefault(SecurityUtils.getCurrentUser().userAccountId()));
    }

    @Override
    public NotificationPreferenceResponse updatePreferences(UpdateNotificationPreferenceRequest request) {
        NotificationPreference preference = getOrCreatePreference(SecurityUtils.getCurrentUser().userAccountId());
        if (request.emailBookingUpdates() != null) {
            preference.setEmailBookingUpdates(request.emailBookingUpdates());
        }
        if (request.emailAccountUpdates() != null) {
            preference.setEmailAccountUpdates(request.emailAccountUpdates());
        }
        if (request.emailReviewUpdates() != null) {
            preference.setEmailReviewUpdates(request.emailReviewUpdates());
        }
        if (request.inAppEnabled() != null) {
            preference.setInAppEnabled(request.inAppEnabled());
        }
        return toPreferenceResponse(preferenceRepository.save(preference));
    }

    private NotificationPreference findPreferenceOrDefault(UUID userId) {
        return preferenceRepository.findByUserAccountId(userId)
                .orElseGet(() -> NotificationPreference.builder()
                        .userAccountId(userId)
                        .emailBookingUpdates(true)
                        .emailAccountUpdates(true)
                        .emailReviewUpdates(true)
                        .inAppEnabled(true)
                        .build());
    }

    private NotificationPreference getOrCreatePreference(UUID userId) {
        return preferenceRepository.findByUserAccountId(userId)
                .orElseGet(() -> preferenceRepository.save(NotificationPreference.builder()
                        .userAccountId(userId)
                        .emailBookingUpdates(true)
                        .emailAccountUpdates(true)
                        .emailReviewUpdates(true)
                        .inAppEnabled(true)
                        .build()));
    }

    private boolean shouldSendEmail(NotificationType type, NotificationPreference preference) {
        if (!notificationProperties.getEmail().isEnabled()) {
            return false;
        }
        return switch (type) {
            case BOOKING_CREATED, BOOKING_ACCEPTED, BOOKING_REJECTED, BOOKING_STARTED,
                 BOOKING_COMPLETED, BOOKING_CANCELLED -> Boolean.TRUE.equals(preference.getEmailBookingUpdates());
            case REVIEW_RECEIVED, REVIEW_MODERATED -> Boolean.TRUE.equals(preference.getEmailReviewUpdates());
            default -> Boolean.TRUE.equals(preference.getEmailAccountUpdates());
        };
    }

    private void queueEmail(Notification notification, String recipientEmail, String subject, String body) {
        EmailOutbox outbox = EmailOutbox.builder()
                .notification(notification)
                .recipientEmail(recipientEmail)
                .subject(subject)
                .bodyHtml("<p>" + body + "</p>")
                .status(OutboxStatus.PENDING)
                .build();
        emailOutboxRepository.save(outbox);
    }

    private void publishRealtimeNotification(Notification notification) {
        if (messagingTemplate == null || notification == null) {
            return;
        }
        try {
            NotificationResponse payload = toResponse(notification);
            // 1. Send to user's personal queue (/user/{userId}/queue/notifications)
            messagingTemplate.convertAndSendToUser(
                    notification.getRecipientUserId().toString(),
                    "/queue/notifications",
                    payload
            );
            // 2. Also broadcast to topic (/topic/notifications/{userId})
            messagingTemplate.convertAndSend(
                    "/topic/notifications/" + notification.getRecipientUserId(),
                    payload
            );
        } catch (Exception ex) {
            log.warn("Failed to publish real-time notification {}: {}", notification.getId(), ex.getMessage());
        }
    }

    private void assertOwner(Notification notification) {
        if (!notification.getRecipientUserId().equals(SecurityUtils.getCurrentUser().userAccountId())) {
            throw new UnauthorizedException("You cannot access this notification");
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getBody(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }

    private NotificationPreferenceResponse toPreferenceResponse(NotificationPreference preference) {
        return new NotificationPreferenceResponse(
                Boolean.TRUE.equals(preference.getEmailBookingUpdates()),
                Boolean.TRUE.equals(preference.getEmailAccountUpdates()),
                Boolean.TRUE.equals(preference.getEmailReviewUpdates()),
                Boolean.TRUE.equals(preference.getInAppEnabled())
        );
    }
}
