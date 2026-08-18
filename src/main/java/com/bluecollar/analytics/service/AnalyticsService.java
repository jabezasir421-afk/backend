package com.bluecollar.analytics.service;

import com.bluecollar.analytics.dto.*;

import java.time.LocalDate;
import java.util.List;

public interface AnalyticsService {

    AnalyticsOverviewResponse getOverview();

    List<DailyBookingStatsResponse> getDailyBookings(LocalDate from, LocalDate to);

    List<TopCategoryResponse> getTopCategories(LocalDate from, LocalDate to, int limit);

    List<TopWorkerResponse> getTopWorkers(LocalDate from, LocalDate to, int limit);

    RatingDistributionResponse getRatingDistribution();

    void refreshSnapshot(LocalDate date);
}
