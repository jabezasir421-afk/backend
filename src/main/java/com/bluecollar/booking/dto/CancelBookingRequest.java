package com.bluecollar.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelBookingRequest(
        @NotBlank(message = "Cancellation reason is required")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason
) {
}
