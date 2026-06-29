package com.bluecollar.availability.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateWorkingHoursRequest(
        @NotEmpty @Size(max = 7) List<WorkingHoursEntry> schedule
) {
    public UpdateWorkingHoursRequest {
        schedule = schedule == null
                ? List.of()
                : List.copyOf(schedule);
    }
}
