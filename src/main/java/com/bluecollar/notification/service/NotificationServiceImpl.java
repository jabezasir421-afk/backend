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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final UserAccountRepository userAccountRepository;
    private final NotificationProperties notificationProperties;

    @Override
    public void notifyUser(
            UUID recipientUserId,
            NotificationType type,
            NotificationChannel channel,
            String title,
            String body,
            String referenceType,
            UUID referenceId
    ) {
        NotificationPreference preference = getOrCreatePreference(recipientUserId);
        if (!Boolean.TRUE.equals(preference.getInAppEnabled()) && channel != NotificationChannel.EMAIL) {
            return;
        }

        Notification notification = Notification.builder()
                .recipientUserId(recipientUserId)
                .type(type)
                .channel(channel)
                .title(title)
                .body(body)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build();
        notificationRepository.save(notification);

        if (shouldSendEmail(type, preference, channel)) {
            userAccountRepository.findById(recipientUserId).ifPresent(user -> queueEmail(notification, user));
        }
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
        return toPreferenceResponse(getOrCreatePreference(SecurityUtils.getCurrentUser().userAccountId()));
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

    private NotificationPreference getOrCreatePreference(UUID userId) {
        return preferenceRepository.findByUserAccountId(userId)
                .orElseGet(() -> preferenceRepository.save(NotificationPreference.builder()
                        .userAccountId(userId)
                        .build()));
    }

    private boolean shouldSendEmail(NotificationType type, NotificationPreference preference, NotificationChannel channel) {
        if (!notificationProperties.getEmail().isEnabled()) {
            return false;
        }
        if (channel == NotificationChannel.IN_APP) {
            return false;
        }
        return switch (type) {
            case BOOKING_CREATED, BOOKING_ACCEPTED, BOOKING_REJECTED, BOOKING_STARTED,
                 BOOKING_COMPLETED, BOOKING_CANCELLED -> Boolean.TRUE.equals(preference.getEmailBookingUpdates());
            case REVIEW_RECEIVED, REVIEW_MODERATED -> Boolean.TRUE.equals(preference.getEmailReviewUpdates());
            default -> Boolean.TRUE.equals(preference.getEmailAccountUpdates());
        };
    }

    private void queueEmail(Notification notification, UserAccount user) {
        EmailOutbox outbox = EmailOutbox.builder()
                .notification(notification)
                .recipientEmail(user.getEmail())
                .subject(notification.getTitle())
                .bodyHtml("<p>" + notification.getBody() + "</p>")
                .status(OutboxStatus.PENDING)
                .build();
        emailOutboxRepository.save(outbox);
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
