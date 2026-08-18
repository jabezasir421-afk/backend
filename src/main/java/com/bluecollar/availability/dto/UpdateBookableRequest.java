package com.bluecollar.availability.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateBookableRequest(
        @NotNull Boolean bookable
) {
}
