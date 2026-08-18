package com.bluecollar.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
        @NotBlank
        @Schema(description = "Password reset token sent to email", example = "eyJ0eXAiOiJKV1QiLCJhbGc...")
        String token,

        @NotBlank
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
                message = "Password must be at least 8 characters with uppercase, lowercase, digit, and special character")
        @Schema(
                description = "New password (8+ chars: uppercase, lowercase, digit, special char)",
                example = "NewPass123!"
        )
        String newPassword
) {
}
