package com.bluecollar.portfolio.dto;

import java.util.List;
import java.util.UUID;

public record PortfolioSummaryResponse(
        UUID workerId,
        String profilePhotoUrl,
        int completionPercent,
        List<PortfolioImageResponse> images,
        List<CertificateResponse> certificates
) {
    public PortfolioSummaryResponse {
        images = images == null
                ? List.of()
                : List.copyOf(images);
        certificates = certificates == null
                ? List.of()
                : List.copyOf(certificates);
    }
}
