package com.bluecollar.review.dto;

import com.bluecollar.review.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportReviewRequest(
        @NotNull ReportReason reason,
        @Size(max = 1000) String description
) {
}
