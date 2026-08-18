package com.bluecollar.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record AddCertificateRequest(
        @NotNull UUID fileId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 200) String issuingOrg,
        LocalDate issueDate,
        LocalDate expiryDate
) {
}
