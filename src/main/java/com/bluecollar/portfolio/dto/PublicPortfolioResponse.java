package com.bluecollar.portfolio.dto;

import java.util.List;
import java.util.UUID;

public record PublicPortfolioResponse(
        UUID workerId,
        String profilePhotoUrl,
        List<PortfolioImageResponse> images,
        List<CertificateResponse> certificates
) {
    public PublicPortfolioResponse {
        images = images == null
                ? List.of()
                : List.copyOf(images);
        certificates = certificates == null
                ? List.of()
                : List.copyOf(certificates);
    }
}
