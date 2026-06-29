package com.bluecollar.availability.dto;

import java.time.LocalTime;

public record AvailableSlotResponse(
        LocalTime startTime,
        LocalTime endTime
) {
}
