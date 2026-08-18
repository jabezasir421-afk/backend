package com.bluecollar.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationEmailRequest(
        @Email
        @NotBlank
        @Schema(description = "Email address to resend verification to", example = "user@example.com")
        String email
) {
}
