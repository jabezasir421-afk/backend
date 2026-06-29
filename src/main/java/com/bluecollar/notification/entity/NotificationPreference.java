package com.bluecollar.notification.entity;

import com.bluecollar.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "notification_preference")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationPreference extends BaseEntity {

    @Column(name = "user_account_id", nullable = false, unique = true)
    private UUID userAccountId;

    @Column(name = "email_booking_updates", nullable = false)
    private Boolean emailBookingUpdates;

    @Column(name = "email_account_updates", nullable = false)
    private Boolean emailAccountUpdates;

    @Column(name = "email_review_updates", nullable = false)
    private Boolean emailReviewUpdates;

    @Column(name = "in_app_enabled", nullable = false)
    private Boolean inAppEnabled;

    @PrePersist
    protected void prePersistPreference() {
        emailBookingUpdates = emailBookingUpdates == null ? Boolean.TRUE : emailBookingUpdates;
        emailAccountUpdates = emailAccountUpdates == null ? Boolean.TRUE : emailAccountUpdates;
        emailReviewUpdates = emailReviewUpdates == null ? Boolean.TRUE : emailReviewUpdates;
        inAppEnabled = inAppEnabled == null ? Boolean.TRUE : inAppEnabled;
    }
}
