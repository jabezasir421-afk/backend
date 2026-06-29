package com.bluecollar.notification.dto;

public record UpdateNotificationPreferenceRequest(
        Boolean emailBookingUpdates,
        Boolean emailAccountUpdates,
        Boolean emailReviewUpdates,
        Boolean inAppEnabled
) {
}
