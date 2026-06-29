package com.bluecollar.skill.dto;

import java.time.Instant;
import java.util.UUID;

public record SkillResponse(
        UUID id,
        String name,
        String description,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
