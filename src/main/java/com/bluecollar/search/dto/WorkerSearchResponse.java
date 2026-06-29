package com.bluecollar.search.dto;

import java.util.List;

public record WorkerSearchResponse(
        List<WorkerSearchResultResponse> results,
        long totalElements,
        int page,
        int size,
        SearchFiltersApplied filters
) {
    public WorkerSearchResponse {
        results = results == null
                ? List.of()
                : List.copyOf(results);
    }
}
