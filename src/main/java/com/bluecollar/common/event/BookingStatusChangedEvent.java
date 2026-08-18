package com.bluecollar.common.event;

import com.bluecollar.booking.entity.BookingStatus;

import java.util.UUID;

public record BookingStatusChangedEvent(
        UUID bookingId,
        BookingStatus oldStatus,
        BookingStatus newStatus,
        UUID customerUserId,
        UUID workerUserId
) {
}
