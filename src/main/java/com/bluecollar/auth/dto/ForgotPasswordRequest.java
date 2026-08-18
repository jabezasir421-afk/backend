package com.bluecollar.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @Email
        @NotBlank
        @Schema(description = "Email address of the account to recover", example = "user@example.com")
        String email
) {
}
