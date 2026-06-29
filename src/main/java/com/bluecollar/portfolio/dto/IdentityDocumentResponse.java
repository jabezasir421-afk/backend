package com.bluecollar.portfolio.dto;

import com.bluecollar.portfolio.entity.DocumentType;
import com.bluecollar.portfolio.entity.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

public record IdentityDocumentResponse(
        UUID id,
        DocumentType documentType,
        VerificationStatus verificationStatus,
        String maskedDocumentNumber,
        Instant createdAt
) {
}
