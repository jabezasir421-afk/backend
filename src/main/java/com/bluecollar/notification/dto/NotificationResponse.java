package com.bluecollar.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String body,
        String referenceType,
        UUID referenceId,
        Instant readAt,
        Instant createdAt
) {
}
