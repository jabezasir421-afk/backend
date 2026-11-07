package com.bluecollar.booking.repository;

import java.util.UUID;

public interface WorkerBookingStatsProjection {
    UUID getWorkerId();
    String getFirstName();
    String getLastName();
    long getCompletedBookings();
}
