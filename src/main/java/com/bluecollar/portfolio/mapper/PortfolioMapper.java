package com.bluecollar.portfolio.mapper;

import com.bluecollar.portfolio.dto.CertificateResponse;
import com.bluecollar.portfolio.dto.IdentityDocumentResponse;
import com.bluecollar.portfolio.dto.PortfolioImageResponse;
import com.bluecollar.portfolio.dto.PortfolioSummaryResponse;
import com.bluecollar.portfolio.dto.PublicPortfolioResponse;
import com.bluecollar.portfolio.entity.WorkerCertificate;
import com.bluecollar.portfolio.entity.WorkerIdentityDocument;
import com.bluecollar.portfolio.entity.WorkerPortfolioItem;
import com.bluecollar.storage.config.StorageProperties;
import com.bluecollar.storage.entity.StoredFile;
import com.bluecollar.storage.repository.StoredFileRepository;
import com.bluecollar.storage.service.FileStorageService;
import com.bluecollar.worker.entity.Worker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PortfolioMapper {

    private final StoredFileRepository storedFileRepository;
    private final FileStorageService fileStorageService;
    private final StorageProperties storageProperties;

    public PortfolioImageResponse toImageResponse(WorkerPortfolioItem item) {
        return new PortfolioImageResponse(
                item.getId(),
                item.getFileId(),
                resolvePublicUrl(item.getFileId()),
                item.getTitle(),
                item.getDescription(),
                item.getDisplayOrder(),
                item.getCreatedAt()
        );
    }

    public CertificateResponse toCertificateResponse(WorkerCertificate certificate) {
        return new CertificateResponse(
                certificate.getId(),
                certificate.getFileId(),
                resolvePublicUrl(certificate.getFileId()),
                certificate.getTitle(),
                certificate.getIssuingOrg(),
                certificate.getIssueDate(),
                certificate.getExpiryDate(),
                certificate.getVerificationStatus(),
                certificate.getCreatedAt()
        );
    }

    public IdentityDocumentResponse toIdentityDocumentResponse(
            WorkerIdentityDocument document,
            String maskedDocumentNumber
    ) {
        return new IdentityDocumentResponse(
                document.getId(),
                document.getDocumentType(),
                document.getVerificationStatus(),
                maskedDocumentNumber,
                document.getCreatedAt()
        );
    }

    public PortfolioSummaryResponse toSummaryResponse(
            Worker worker,
            List<PortfolioImageResponse> images,
            List<CertificateResponse> certificates
    ) {
        return new PortfolioSummaryResponse(
                worker.getId(),
                resolvePublicUrl(worker.getProfilePhotoFileId()),
                worker.getProfileCompletionPercent() == null ? 0 : worker.getProfileCompletionPercent(),
                images,
                certificates
        );
    }

    public PublicPortfolioResponse toPublicPortfolioResponse(
            Worker worker,
            List<PortfolioImageResponse> images,
            List<CertificateResponse> certificates
    ) {
        return new PublicPortfolioResponse(
                worker.getId(),
                resolvePublicUrl(worker.getProfilePhotoFileId()),
                images,
                certificates
        );
    }

    public String resolvePublicUrl(UUID fileId) {
        if (fileId == null) {
            return null;
        }
        return storedFileRepository.findByIdAndActiveTrue(fileId)
                .map(this::buildDownloadUrl)
                .orElse(null);
    }

    private String buildDownloadUrl(StoredFile storedFile) {
        return fileStorageService.getDownloadUrl(
                storedFile.getStorageKey(),
                storageProperties.getPresignedUrlExpirySeconds()
        );
    }

    public static String maskDocumentNumber(String documentNumber) {
        if (documentNumber == null || documentNumber.isBlank()) {
            return null;
        }
        String trimmed = documentNumber.trim();
        if (trimmed.length() <= 4) {
            return "X".repeat(Math.max(0, trimmed.length() - 1)) + trimmed.substring(trimmed.length() - 1);
        }
        return "X".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }
}
