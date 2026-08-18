package com.bluecollar.portfolio.controller;

import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.portfolio.dto.*;
import com.bluecollar.portfolio.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Portfolio", description = "Worker portfolio management including images, certificates, and identity documents")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping("/api/v1/workers/me/portfolio")
    @PreAuthorize("hasRole('WORKER')")
    @Operation(
            summary = "Get my portfolio",
            description = "Retrieve authenticated worker's portfolio including images, certificates, and completion status.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<PortfolioSummaryResponse>> getMyPortfolio() {
        return ResponseEntity.ok(ApiResponse.success(portfolioService.getMyPortfolio(), "Portfolio fetched successfully"));
    }

    @GetMapping("/api/v1/workers/me/portfolio/completion")
    @PreAuthorize("hasRole('WORKER')")
    @Operation(
            summary = "Get profile completion percentage",
            description = "Retrieve the authenticated worker's profile completion percentage (0-100%).",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<ProfileCompletionResponse>> getProfileCompletion() {
        return ResponseEntity.ok(ApiResponse.success(
                portfolioService.getProfileCompletion(),
                "Profile completion fetched successfully"
        ));
    }

    @PostMapping("/api/v1/workers/me/portfolio/images")
    @PreAuthorize("hasRole('WORKER')")
    @Operation(
            summary = "Add portfolio image",
            description = "Add an image to the authenticated worker's portfolio.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<PortfolioImageResponse>> addPortfolioImage(
            @Valid @RequestBody AddPortfolioImageRequest request
    ) {
        PortfolioImageResponse response = portfolioService.addPortfolioImage(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Portfolio image added successfully"));
    }

    @PutMapping("/api/v1/workers/me/portfolio/images/{id}")
    @PreAuthorize("hasRole('WORKER')")
    @Operation(
            summary = "Update portfolio image",
            description = "Update metadata (description, order) for a portfolio image.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<PortfolioImageResponse>> updatePortfolioImage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePortfolioImageRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                portfolioService.updatePortfolioImage(id, request),
                "Portfolio image updated successfully"
        ));
    }

    @DeleteMapping("/api/v1/workers/me/portfolio/images/{id}")
    @PreAuthorize("hasRole('WORKER')")
    @Operation(
            summary = "Delete portfolio image",
            description = "Remove an image from the authenticated worker's portfolio.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> deletePortfolioImage(@PathVariable UUID id) {
        portfolioService.deletePortfolioImage(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Portfolio image deleted successfully"));
    }

    @PostMapping("/api/v1/workers/me/portfolio/certificates")
    @PreAuthorize("hasRole('WORKER')")
    @Operation(
            summary = "Add certificate",
            description = "Add a professional certificate to the authenticated worker's portfolio.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<CertificateResponse>> addCertificate(
            @Valid @RequestBody AddCertificateRequest request
    ) {
        CertificateResponse response = portfolioService.addCertificate(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Certificate added successfully"));
    }

    @PutMapping("/api/v1/workers/me/portfolio/certificates/{id}")
    @PreAuthorize("hasRole('WORKER')")
    @Operation(
            summary = "Update certificate",
            description = "Update certificate information (title, issuer, issue/expiry dates).",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<CertificateResponse>> updateCertificate(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCertificateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                portfolioService.updateCertificate(id, request),
                "Certificate updated successfully"
        ));
    }

    @DeleteMapping("/api/v1/workers/me/portfolio/certificates/{id}")
    @PreAuthorize("hasRole('WORKER')")
    @Operation(
            summary = "Delete certificate",
            description = "Remove a certificate from the authenticated worker's portfolio.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> deleteCertificate(@PathVariable UUID id) {
        portfolioService.deleteCertificate(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Certificate deleted successfully"));
    }

    @PostMapping("/api/v1/workers/me/portfolio/identity-documents")
    @PreAuthorize("hasRole('WORKER')")
    @Operation(
            summary = "Submit an identity document",
            description = "Upload an identity document for verification. Documents are immutable once submitted for compliance/audit purposes.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<IdentityDocumentResponse>> addIdentityDocument(
            @Valid @RequestBody AddIdentityDocumentRequest request
    ) {
        IdentityDocumentResponse response = portfolioService.addIdentityDocument(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Identity document submitted successfully"));
    }

    @GetMapping("/api/v1/workers/me/portfolio/identity-documents")
    @PreAuthorize("hasRole('WORKER')")
    @Operation(
            summary = "Get submitted identity documents",
            description = "Retrieve all identity documents submitted for verification. Shows document type, verification status, and any rejection reasons.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<List<IdentityDocumentResponse>>> getMyIdentityDocuments() {
        return ResponseEntity.ok(ApiResponse.success(
                portfolioService.getMyIdentityDocuments(),
                "Identity documents fetched successfully"
        ));
    }

    @DeleteMapping("/api/v1/workers/me/portfolio/identity-documents/{id}")
    @PreAuthorize("hasRole('WORKER')")
    @Operation(
            summary = "Deactivate an identity document",
            description = "Soft-delete (deactivate) an identity document. The document remains in the audit trail for compliance but is no longer active. " +
                    "Cannot deactivate verified documents—contact admin if correction is needed.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> deleteIdentityDocument(@PathVariable UUID id) {
        portfolioService.deleteIdentityDocument(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Identity document deactivated successfully"));
    }

    @PutMapping("/api/v1/workers/me/profile-photo")
    @PreAuthorize("hasRole('WORKER')")
    @Operation(
            summary = "Update profile photo",
            description = "Set or update the authenticated worker's profile photo.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<PortfolioSummaryResponse>> setProfilePhoto(
            @Valid @RequestBody SetProfilePhotoRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                portfolioService.setProfilePhoto(request),
                "Profile photo updated successfully"
        ));
    }

    @GetMapping("/api/v1/workers/{id}/portfolio")
    @Operation(
            summary = "Get worker's public portfolio",
            description = "Retrieve a worker's public portfolio including profile photo, portfolio images, and verified certificates. " +
                    "Does not include profile completion percentage (private metric).",
            security = {}
    )
    public ResponseEntity<ApiResponse<PublicPortfolioResponse>> getPublicPortfolio(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                portfolioService.getPublicPortfolio(id),
                "Public portfolio fetched successfully"
        ));
    }
}
