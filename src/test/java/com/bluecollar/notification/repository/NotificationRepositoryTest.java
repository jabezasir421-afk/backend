package com.bluecollar.notification.repository;

import com.bluecollar.notification.entity.Notification;
import com.bluecollar.notification.entity.NotificationChannel;
import com.bluecollar.notification.entity.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    private UUID recipientUserId;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        recipientUserId = UUID.randomUUID();
    }

    @Test
    void countByRecipientUserIdAndActiveTrueAndReadAtIsNullShouldReturnUnreadCount() {
        notificationRepository.saveAndFlush(buildNotification(recipientUserId, null));
        notificationRepository.saveAndFlush(buildNotification(recipientUserId, null));
        notificationRepository.saveAndFlush(buildNotification(recipientUserId, Instant.now()));

        long unreadCount = notificationRepository.countByRecipientUserIdAndActiveTrueAndReadAtIsNull(recipientUserId);

        assertEquals(2L, unreadCount);
    }

    @Test
    void markAllReadShouldMarkAllUnreadNotificationsAsRead() {
        notificationRepository.saveAndFlush(buildNotification(recipientUserId, null));
        notificationRepository.saveAndFlush(buildNotification(recipientUserId, null));
        notificationRepository.saveAndFlush(
                buildNotification(recipientUserId, Instant.now().minusSeconds(60))
        );

        int updated = notificationRepository.markAllRead(recipientUserId, Instant.now());

        assertEquals(2, updated);
        assertEquals(0L, notificationRepository.countByRecipientUserIdAndActiveTrueAndReadAtIsNull(recipientUserId));
    }

    private Notification buildNotification(UUID recipientUserId, Instant readAt) {
        return Notification.builder()
                .recipientUserId(recipientUserId)
                .type(NotificationType.BOOKING_CREATED)
                .channel(NotificationChannel.IN_APP)
                .title("Booking created")
                .body("Your booking was created")
                .referenceType("BOOKING")
                .referenceId(UUID.randomUUID())
                .readAt(readAt)
                .active(true)
                .build();
    }
}
