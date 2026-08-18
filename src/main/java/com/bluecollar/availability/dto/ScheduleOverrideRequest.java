package com.bluecollar.availability.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleOverrideRequest(
        @NotNull @FutureOrPresent LocalDate overrideDate,
        @NotNull Boolean available,
        LocalTime startTime,
        LocalTime endTime,
        @Size(max = 200) String reason
) {
}
