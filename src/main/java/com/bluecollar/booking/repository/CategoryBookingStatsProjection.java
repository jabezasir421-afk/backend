package com.bluecollar.booking.repository;

import java.util.UUID;

public interface CategoryBookingStatsProjection {
    UUID getCategoryId();
    String getCategoryName();
    long getBookingCount();
}
