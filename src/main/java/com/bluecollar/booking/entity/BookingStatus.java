package com.bluecollar.booking.entity;

public enum BookingStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(BookingStatus target) {
        return switch (this) {
            case PENDING -> target == ACCEPTED || target == REJECTED || target == CANCELLED;
            case ACCEPTED -> target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
            case REJECTED, COMPLETED, CANCELLED -> false;
        };
    }
}
