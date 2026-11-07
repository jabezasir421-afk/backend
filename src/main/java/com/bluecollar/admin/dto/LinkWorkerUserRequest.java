package com.bluecollar.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LinkWorkerUserRequest(
        @NotNull(message = "User account ID is required")
        @Schema(description = "ID of the user account to link to this worker profile")
        UUID userAccountId
) {
}
