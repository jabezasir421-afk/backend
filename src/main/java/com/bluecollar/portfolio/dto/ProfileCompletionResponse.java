package com.bluecollar.portfolio.dto;

import java.util.List;
import java.util.Map;

public record ProfileCompletionResponse(
        int completionPercent,
        Map<String, Boolean> sections,
        List<String> missingFields
) {
    public ProfileCompletionResponse {
        missingFields = missingFields == null
                ? List.of()
                : List.copyOf(missingFields);
        sections = sections == null
                ? Map.of()
                : Map.copyOf(sections);
    }
}
