package com.bluecollar.availability.dto;

import com.bluecollar.worker.entity.OnlineStatus;

import java.time.LocalDate;
import java.util.List;

public record AvailabilitySummaryResponse(
        boolean bookable,
        OnlineStatus onlineStatus,
        boolean vacationMode,
        LocalDate vacationStart,
        LocalDate vacationEnd,
        List<WorkingHoursEntry> workingHours
) {
    public AvailabilitySummaryResponse {
        workingHours = workingHours == null
                ? List.of()
                : List.copyOf(workingHours);
    }
}
