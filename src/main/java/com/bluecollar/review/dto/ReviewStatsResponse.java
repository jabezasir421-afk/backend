package com.bluecollar.review.dto;

import java.math.BigDecimal;
import java.util.Map;

public record ReviewStatsResponse(
        BigDecimal averageRating,
        long totalReviews,
        Map<Short, Long> ratingDistribution
) {
    public ReviewStatsResponse {
        ratingDistribution = ratingDistribution == null
                ? Map.of()
                : Map.copyOf(ratingDistribution);
    }
}
