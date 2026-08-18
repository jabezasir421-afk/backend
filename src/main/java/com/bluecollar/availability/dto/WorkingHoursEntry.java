package com.bluecollar.availability.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record WorkingHoursEntry(
        @NotNull(message = "Day of week is required")
        @Min(value = 1, message = "Day of week must be between 1 (Monday) and 7 (Sunday)")
        @Max(value = 7, message = "Day of week must be between 1 (Monday) and 7 (Sunday)")
        Short dayOfWeek,
        @NotNull(message = "Start time is required")
        LocalTime startTime,
        @NotNull(message = "End time is required")
        LocalTime endTime
) {
}
