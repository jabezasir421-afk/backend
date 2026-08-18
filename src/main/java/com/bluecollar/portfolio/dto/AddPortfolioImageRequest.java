package com.bluecollar.portfolio.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AddPortfolioImageRequest(
        @NotNull UUID fileId,
        @Size(max = 200) String title,
        @Size(max = 1000) String description,
        @Min(0) @Max(99) Short displayOrder
) {
}
