package com.bluecollar.search.service;

import com.bluecollar.search.dto.WorkerSearchResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WorkerSearchService {

    WorkerSearchResponse searchWorkers(
            String city,
            String state,
            UUID categoryId,
            List<UUID> skillIds,
            boolean matchAnySkill,
            BigDecimal minRating,
            Boolean available,
            LocalDate availableOn,
            Integer minExperience,
            Integer maxExperience,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean verified,
            String sort,
            String direction,
            Pageable pageable
    );
}
