package com.bluecollar.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectDocumentRequest(
        @NotBlank(message = "Rejection reason is required")
        @Size(max = 500, message = "Rejection reason must not exceed 500 characters")
        @Schema(description = "Reason for rejecting the identity document", example = "Document image is blurred and unreadable")
        String reason
) {
}
