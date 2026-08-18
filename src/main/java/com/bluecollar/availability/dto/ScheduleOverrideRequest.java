package com.bluecollar.availability.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleOverrideRequest(
        @NotNull
        @FutureOrPresent
        @Schema(description = "Date for the schedule override", example = "2026-08-25")
        LocalDate overrideDate,
        @NotNull
        @Schema(description = "Whether the worker is available on this date. If true, optionally provide startTime and endTime")
        Boolean available,
        @Schema(description = "Start time in ISO-8601 format (HH:mm:ss). Required if available=true", example = "09:00:00", format = "time")
        LocalTime startTime,
        @Schema(description = "End time in ISO-8601 format (HH:mm:ss). Required if available=true", example = "18:00:00", format = "time")
        LocalTime endTime,
        @Size(max = 200)
        @Schema(description = "Optional reason for the schedule override")
        String reason
) {
}
