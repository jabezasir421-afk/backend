package com.bluecollar.availability.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ScheduleOverrideResponse(
        UUID id,
        LocalDate overrideDate,
        boolean available,
        WorkingHoursEntry hours,
        String reason
) {
}
