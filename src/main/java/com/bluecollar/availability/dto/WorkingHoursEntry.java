package com.bluecollar.availability.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record WorkingHoursEntry(
        @NotNull(message = "Day of week is required")
        @Min(value = 1, message = "Day of week must be between 1 (Monday) and 7 (Sunday)")
        @Max(value = 7, message = "Day of week must be between 1 (Monday) and 7 (Sunday)")
        @Schema(description = "Day of week: 1=Monday, 2=Tuesday, ..., 7=Sunday", example = "1")
        Short dayOfWeek,
        @NotNull(message = "Start time is required")
        @Schema(description = "Start time in ISO-8601 format (HH:mm:ss)", example = "09:00:00", format = "time")
        LocalTime startTime,
        @NotNull(message = "End time is required")
        @Schema(description = "End time in ISO-8601 format (HH:mm:ss)", example = "18:00:00", format = "time")
        LocalTime endTime
) {
}
