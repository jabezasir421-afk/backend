package com.bluecollar.portfolio.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateCertificateRequest(
        @Size(max = 200) String title,
        @Size(max = 200) String issuingOrg,
        LocalDate issueDate,
        LocalDate expiryDate
) {
}
