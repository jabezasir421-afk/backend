package com.bluecollar.search.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SearchFiltersApplied(
        String city,
        UUID categoryId,
        List<UUID> skillIds,
        BigDecimal minRating,
        Boolean available,
        LocalDate availableOn,
        Integer minExperience,
        Integer maxExperience,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
    public SearchFiltersApplied {
        skillIds = skillIds == null
                ? List.of()
                : List.copyOf(skillIds);
    }
}
