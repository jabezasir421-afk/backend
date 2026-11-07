package com.bluecollar.admin.dto;

import com.bluecollar.portfolio.entity.DocumentType;
import com.bluecollar.portfolio.entity.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminIdentityDocumentResponse(
        UUID id,
        UUID workerId,
        String workerName,
        UUID fileId,
        DocumentType documentType,
        String maskedDocumentNumber,
        VerificationStatus verificationStatus,
        UUID verifiedBy,
        Instant verifiedAt,
        String rejectionReason,
        Instant createdAt
) {
}
