package com.bluecollar.notification.listener;

import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.common.event.BookingCreatedEvent;
import com.bluecollar.common.event.BookingStatusChangedEvent;
import com.bluecollar.common.event.ReviewCreatedEvent;
import com.bluecollar.notification.entity.NotificationChannel;
import com.bluecollar.notification.entity.NotificationType;
import com.bluecollar.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCreated(BookingCreatedEvent event) {
        if (event.workerUserId() != null) {
            notificationService.notifyUser(
                    event.workerUserId(),
                    NotificationType.BOOKING_CREATED,
                    NotificationChannel.BOTH,
                    "New booking request",
                    "You have received a new booking request.",
                    "BOOKING",
                    event.bookingId()
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingStatusChanged(BookingStatusChangedEvent event) {
        NotificationType type = mapBookingStatus(event.newStatus());
        if (type == null) {
            return;
        }
        if (event.customerUserId() != null) {
            notificationService.notifyUser(
                    event.customerUserId(),
                    type,
                    NotificationChannel.BOTH,
                    "Booking update",
                    "Your booking status changed to " + event.newStatus().name() + ".",
                    "BOOKING",
                    event.bookingId()
            );
        }
        if (event.workerUserId() != null) {
            notificationService.notifyUser(
                    event.workerUserId(),
                    type,
                    NotificationChannel.BOTH,
                    "Booking update",
                    "Booking status changed to " + event.newStatus().name() + ".",
                    "BOOKING",
                    event.bookingId()
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewCreated(ReviewCreatedEvent event) {
        if (event.workerUserId() != null) {
            notificationService.notifyUser(
                    event.workerUserId(),
                    NotificationType.REVIEW_RECEIVED,
                    NotificationChannel.BOTH,
                    "New review received",
                    "You received a " + event.rating() + "-star review.",
                    "REVIEW",
                    event.reviewId()
            );
        }
    }

    private NotificationType mapBookingStatus(BookingStatus status) {
        return switch (status) {
            case ACCEPTED -> NotificationType.BOOKING_ACCEPTED;
            case REJECTED -> NotificationType.BOOKING_REJECTED;
            case IN_PROGRESS -> NotificationType.BOOKING_STARTED;
            case COMPLETED -> NotificationType.BOOKING_COMPLETED;
            case CANCELLED -> NotificationType.BOOKING_CANCELLED;
            default -> null;
        };
    }
}
