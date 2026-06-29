package com.bluecollar.availability.dto;

import java.util.List;

public record WorkerAvailabilityConfigResponse(
        AvailabilitySummaryResponse summary,
        List<ScheduleOverrideResponse> overrides
) {
    public WorkerAvailabilityConfigResponse {
        overrides = overrides == null
                ? List.of()
                : List.copyOf(overrides);
    }
}
