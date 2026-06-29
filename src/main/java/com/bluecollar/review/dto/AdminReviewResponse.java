package com.bluecollar.review.dto;

import com.bluecollar.review.entity.ModerationStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminReviewResponse(
        UUID id,
        UUID workerId,
        UUID customerId,
        short rating,
        String comment,
        ModerationStatus moderationStatus,
        Instant moderatedAt,
        long reportCount,
        boolean active
) {
}
