package com.bluecollar.booking.dto;

import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.customer.dto.CustomerSummaryResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        BookingStatus status,
        LocalDate scheduledDate,
        String timeSlot,
        String description,
        BigDecimal quotedAmount,
        BigDecimal finalAmount,
        String cancellationReason,
        Instant acceptedAt,
        Instant completedAt,
        Instant cancelledAt,
        UUID categoryId,
        String categoryName,
        UUID workerId,
        String workerName,
        CustomerSummaryResponse customer,
        String addressLine1,
        String addressCity,
        String addressState,
        String addressPincode,
        Instant createdAt,
        Instant updatedAt
) {
}
