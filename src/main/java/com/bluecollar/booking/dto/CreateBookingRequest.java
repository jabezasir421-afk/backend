package com.bluecollar.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull(message = "Worker is required")
        UUID workerId,

        @NotNull(message = "Category is required")
        UUID categoryId,

        @NotNull(message = "Address is required")
        UUID addressId,

        @NotNull(message = "Scheduled date is required")
        @FutureOrPresent(message = "Scheduled date must be today or in the future")
        LocalDate scheduledDate,

        @NotBlank(message = "Time slot is required")
        @Pattern(regexp = "^\\d{2}:\\d{2}-\\d{2}:\\d{2}$", message = "Time slot must be in HH:MM-HH:MM format")
        @Schema(description = "Time slot in HH:MM-HH:MM format (e.g., 09:00-18:00)", example = "09:00-18:00")
        String timeSlot,

        @NotBlank(message = "Description is required")
        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description
) {
}
