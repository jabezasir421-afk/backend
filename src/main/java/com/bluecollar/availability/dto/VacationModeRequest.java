package com.bluecollar.availability.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record VacationModeRequest(
        @NotNull Boolean enabled,
        @FutureOrPresent LocalDate vacationStart,
        LocalDate vacationEnd
) {
}
