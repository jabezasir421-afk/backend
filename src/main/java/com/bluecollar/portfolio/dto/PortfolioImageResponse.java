package com.bluecollar.portfolio.dto;

import java.time.Instant;
import java.util.UUID;

public record PortfolioImageResponse(
        UUID id,
        UUID fileId,
        String imageUrl,
        String title,
        String description,
        Short displayOrder,
        Instant createdAt
) {
}
