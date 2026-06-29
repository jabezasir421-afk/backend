package com.bluecollar.notification.dto;

public record NotificationPreferenceResponse(
        boolean emailBookingUpdates,
        boolean emailAccountUpdates,
        boolean emailReviewUpdates,
        boolean inAppEnabled
) {
}
