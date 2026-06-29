package com.bluecollar.customer.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        UUID userAccountId,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String avatarUrl,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
