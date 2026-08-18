package com.bluecollar.analytics.dto;

import java.math.BigDecimal;

public record AnalyticsOverviewResponse(
        long totalWorkers,
        long activeWorkers,
        long totalCustomers,
        long activeCustomers,
        long totalBookings,
        long bookingsToday,
        BigDecimal averagePlatformRating,
        BigDecimal totalRevenue
) {
}
