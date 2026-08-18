package com.bluecollar.booking.dto;

import java.math.BigDecimal;

public record CompleteBookingRequest(
        BigDecimal finalAmount
) {
}
