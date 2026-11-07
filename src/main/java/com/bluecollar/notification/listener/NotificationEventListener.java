package com.bluecollar.notification.listener;

import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.common.event.BookingCreatedEvent;
import com.bluecollar.common.event.BookingStatusChangedEvent;
import com.bluecollar.common.event.ReviewCreatedEvent;
import com.bluecollar.notification.entity.NotificationChannel;
import com.bluecollar.notification.entity.NotificationType;
import com.bluecollar.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingCreated(BookingCreatedEvent event) {
        try {
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
        } catch (Exception ex) {
            log.error("Failed to process booking created notification for booking {}", event.bookingId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingStatusChanged(BookingStatusChangedEvent event) {
        try {
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
        } catch (Exception ex) {
            log.error("Failed to process booking status changed notification for booking {}", event.bookingId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReviewCreated(ReviewCreatedEvent event) {
        try {
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
        } catch (Exception ex) {
            log.error("Failed to process review created notification for review {}", event.reviewId(), ex);
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
