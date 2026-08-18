package com.bluecollar.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelBookingRequest(
        @NotBlank(message = "Cancellation reason is required")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        @Schema(description = "Reason for cancelling the booking", example = "Emergency came up")
        String reason
) {
}
