package com.bluecollar.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CompleteBookingRequest(
        @NotNull(message = "Final amount is required")
        @Positive(message = "Final amount must be greater than zero")
        @Schema(description = "Final amount to charge for the completed booking", example = "150.00")
        BigDecimal finalAmount
) {
}
