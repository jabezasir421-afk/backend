package com.bluecollar.availability.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateWorkingHoursRequest(
        @NotEmpty
        @Size(max = 7)
        @Schema(description = "List of working hours for each day of the week (max 7 entries, one per day)")
        List<WorkingHoursEntry> schedule
) {
    public UpdateWorkingHoursRequest {
        schedule = schedule == null
                ? List.of()
                : List.copyOf(schedule);
    }
}
