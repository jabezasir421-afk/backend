package com.bluecollar.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
        long totalCustomers,
        long totalWorkers,
        long totalBookings,
        long activeBookings,
        BigDecimal completionRate,
        List<CategoryDistributionResponse> categoriesDistribution,
        List<BookingsTrendResponse> bookingsTrend
) {
    public DashboardResponse {
        bookingsTrend = bookingsTrend == null
                ? List.of()
                : List.copyOf(bookingsTrend);

        categoriesDistribution = categoriesDistribution == null
                ? List.of()
                : List.copyOf(categoriesDistribution);
    }
}
