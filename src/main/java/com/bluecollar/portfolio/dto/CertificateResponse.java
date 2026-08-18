package com.bluecollar.portfolio.dto;

import com.bluecollar.portfolio.entity.VerificationStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CertificateResponse(
        UUID id,
        UUID fileId,
        String downloadUrl,
        String title,
        String issuingOrg,
        LocalDate issueDate,
        LocalDate expiryDate,
        VerificationStatus verificationStatus,
        Instant createdAt
) {
}
