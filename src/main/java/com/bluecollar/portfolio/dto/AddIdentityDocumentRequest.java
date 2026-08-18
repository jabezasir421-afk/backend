package com.bluecollar.portfolio.dto;

import com.bluecollar.portfolio.entity.DocumentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AddIdentityDocumentRequest(
        @NotNull UUID fileId,
        @NotNull DocumentType documentType,
        @Size(max = 50) String documentNumber
) {
}
