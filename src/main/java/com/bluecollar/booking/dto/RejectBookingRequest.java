package com.bluecollar.booking.dto;

import jakarta.validation.constraints.Size;

public record RejectBookingRequest(
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason
) {
}
