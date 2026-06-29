package com.bluecollar.admin.dto;

import java.time.LocalDate;

public record BookingsTrendResponse(
        LocalDate date,
        long count
) {
}
