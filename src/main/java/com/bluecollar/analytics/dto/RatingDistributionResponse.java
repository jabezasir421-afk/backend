package com.bluecollar.analytics.dto;

import java.math.BigDecimal;
import java.util.Map;

public record RatingDistributionResponse(Map<Short, Long> distribution, BigDecimal average, long totalReviews) {

    public RatingDistributionResponse {
        distribution = distribution == null
                ? Map.of()
                : Map.copyOf(distribution);
    }
}
