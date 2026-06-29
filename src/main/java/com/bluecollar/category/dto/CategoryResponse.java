package com.bluecollar.category.dto;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String description,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
