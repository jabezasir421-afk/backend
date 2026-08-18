package com.bluecollar.review.dto;

import jakarta.validation.constraints.Size;

public record ModerateReviewRequest(
        @Size(max = 500) String notes
) {
}
