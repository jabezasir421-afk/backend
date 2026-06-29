package com.bluecollar.search.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record WorkerSearchResultResponse(
        UUID id,
        String fullName,
        String primaryCity,
        String categoryName,
        BigDecimal hourlyRate,
        BigDecimal averageRating,
        int reviewCount,
        boolean available,
        boolean verified,
        String profilePhotoUrl,
        List<String> skillNames,
        int profileCompletionPercent
) {
    public WorkerSearchResultResponse {
        skillNames = skillNames == null
                ? List.of()
                : List.copyOf(skillNames);
    }
}
