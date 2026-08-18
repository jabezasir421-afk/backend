package com.bluecollar.notification.service;

import com.bluecollar.notification.dto.NotificationPreferenceResponse;
import com.bluecollar.notification.dto.NotificationResponse;
import com.bluecollar.notification.dto.UpdateNotificationPreferenceRequest;
import com.bluecollar.notification.entity.NotificationChannel;
import com.bluecollar.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {

    void notifyUser(
            UUID recipientUserId,
            NotificationType type,
            NotificationChannel channel,
            String title,
            String body,
            String referenceType,
            UUID referenceId
    );

    Page<NotificationResponse> getMyNotifications(Pageable pageable);

    long getUnreadCount();

    void markAsRead(UUID notificationId);

    void markAllAsRead();

    NotificationPreferenceResponse getPreferences();

    NotificationPreferenceResponse updatePreferences(UpdateNotificationPreferenceRequest request);
}
