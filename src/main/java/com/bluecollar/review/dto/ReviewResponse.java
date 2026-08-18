package com.bluecollar.review.dto;

import com.bluecollar.customer.dto.CustomerSummaryResponse;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID bookingId,
        UUID workerId,
        CustomerSummaryResponse customer,
        Short rating,
        String comment,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
