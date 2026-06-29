package com.bluecollar.common.event;

import java.util.UUID;

public record BookingCreatedEvent(UUID bookingId, UUID customerUserId, UUID workerUserId) {
}
