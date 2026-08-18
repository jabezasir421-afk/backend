package com.bluecollar.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank
        @Schema(description = "Email verification token sent to email", example = "eyJ0eXAiOiJKV1QiLCJhbGc...")
        String token
) {
}
