package com.bluecollar.search.controller;

import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.search.dto.WorkerSearchResponse;
import com.bluecollar.search.service.WorkerSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final WorkerSearchService workerSearchService;

    @GetMapping("/workers")
    public ResponseEntity<ApiResponse<WorkerSearchResponse>> searchWorkers(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) List<UUID> skillIds,
            @RequestParam(required = false, defaultValue = "false") boolean matchAnySkill,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) LocalDate availableOn,
            @RequestParam(required = false) Integer minExperience,
            @RequestParam(required = false) Integer maxExperience,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false, defaultValue = "true") Boolean verified,
            @RequestParam(required = false, defaultValue = "createdAt") String sort,
            @RequestParam(required = false, defaultValue = "desc") String direction,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        WorkerSearchResponse response = workerSearchService.searchWorkers(
                city, state, categoryId, skillIds, matchAnySkill, minRating, available,
                availableOn, minExperience, maxExperience, minPrice, maxPrice, verified,
                sort, direction, pageable
        );
        return ResponseEntity.ok(ApiResponse.success(response, "Workers fetched successfully"));
    }
}
