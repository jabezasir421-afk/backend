package com.bluecollar.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateProfilePhotoRequest(
        @NotNull
        @Schema(description = "File ID of the uploaded profile photo", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID fileId
) {
}
