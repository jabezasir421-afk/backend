package com.bluecollar.portfolio.service;

import com.bluecollar.common.exception.UnauthorizedException;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.common.service.DocumentEncryptionService;
import com.bluecollar.portfolio.dto.*;
import com.bluecollar.portfolio.entity.WorkerCertificate;
import com.bluecollar.portfolio.entity.WorkerIdentityDocument;
import com.bluecollar.portfolio.entity.WorkerPortfolioItem;
import com.bluecollar.portfolio.exception.MaxPortfolioItemsExceededException;
import com.bluecollar.portfolio.exception.PortfolioItemNotFoundException;
import com.bluecollar.portfolio.mapper.PortfolioMapper;
import com.bluecollar.portfolio.repository.WorkerCertificateRepository;
import com.bluecollar.portfolio.repository.WorkerIdentityDocumentRepository;
import com.bluecollar.portfolio.repository.WorkerPortfolioItemRepository;
import com.bluecollar.storage.entity.FileCategory;
import com.bluecollar.storage.entity.StoredFile;
import com.bluecollar.storage.exception.FileNotFoundException;
import com.bluecollar.storage.repository.StoredFileRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.exception.WorkerNotFoundException;
import com.bluecollar.worker.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PortfolioServiceImpl implements PortfolioService {

    private static final int MAX_PORTFOLIO_IMAGES = 20;
    private static final int MAX_CERTIFICATES = 10;
    private static final int MAX_IDENTITY_DOCS = 3;

    private final WorkerRepository workerRepository;
    private final WorkerPortfolioItemRepository portfolioItemRepository;
    private final WorkerCertificateRepository certificateRepository;
    private final WorkerIdentityDocumentRepository identityDocumentRepository;
    private final StoredFileRepository storedFileRepository;
    private final PortfolioMapper portfolioMapper;
    private final ProfileCompletionService profileCompletionService;
    private final DocumentEncryptionService documentEncryptionService;

    @Override
    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getMyPortfolio() {
        Worker worker = findWorkerByCurrentUser();
        return buildSummary(worker);
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileCompletionResponse getProfileCompletion() {
        Worker worker = findWorkerByCurrentUser();
        return profileCompletionService.calculate(worker);
    }

    @Override
    public PortfolioImageResponse addPortfolioImage(AddPortfolioImageRequest request) {
        Worker worker = findWorkerByCurrentUser();
        if (portfolioItemRepository.countByWorkerIdAndActiveTrue(worker.getId()) >= MAX_PORTFOLIO_IMAGES) {
            throw new MaxPortfolioItemsExceededException("portfolio images", MAX_PORTFOLIO_IMAGES);
        }

        validateOwnedFile(worker, request.fileId(), FileCategory.PORTFOLIO_IMAGE);

        WorkerPortfolioItem item = WorkerPortfolioItem.builder()
                .worker(worker)
                .fileId(request.fileId())
                .title(trimToNull(request.title()))
                .description(trimToNull(request.description()))
                .displayOrder(
                        request.displayOrder() == null
                                ? Short.valueOf((short) 0)
                                : request.displayOrder()
                )
                .build();

        WorkerPortfolioItem saved = portfolioItemRepository.save(item);
        profileCompletionService.recalculateAndSave(worker);
        return portfolioMapper.toImageResponse(saved);
    }

    @Override
    public PortfolioImageResponse updatePortfolioImage(UUID id, UpdatePortfolioImageRequest request) {
        Worker worker = findWorkerByCurrentUser();
        WorkerPortfolioItem item = findPortfolioItem(worker.getId(), id);

        if (request.title() != null) {
            item.setTitle(trimToNull(request.title()));
        }
        if (request.description() != null) {
            item.setDescription(trimToNull(request.description()));
        }
        if (request.displayOrder() != null) {
            item.setDisplayOrder(request.displayOrder());
        }

        return portfolioMapper.toImageResponse(portfolioItemRepository.save(item));
    }

    @Override
    public void deletePortfolioImage(UUID id) {
        Worker worker = findWorkerByCurrentUser();
        WorkerPortfolioItem item = findPortfolioItem(worker.getId(), id);
        item.setActive(false);
        portfolioItemRepository.save(item);
        profileCompletionService.recalculateAndSave(worker);
    }

    @Override
    public CertificateResponse addCertificate(AddCertificateRequest request) {
        Worker worker = findWorkerByCurrentUser();
        if (certificateRepository.countByWorkerIdAndActiveTrue(worker.getId()) >= MAX_CERTIFICATES) {
            throw new MaxPortfolioItemsExceededException("certificates", MAX_CERTIFICATES);
        }

        validateOwnedFile(worker, request.fileId(), FileCategory.CERTIFICATE);

        WorkerCertificate certificate = WorkerCertificate.builder()
                .worker(worker)
                .fileId(request.fileId())
                .title(request.title().trim())
                .issuingOrg(trimToNull(request.issuingOrg()))
                .issueDate(request.issueDate())
                .expiryDate(request.expiryDate())
                .build();

        WorkerCertificate saved = certificateRepository.save(certificate);
        profileCompletionService.recalculateAndSave(worker);
        return portfolioMapper.toCertificateResponse(saved);
    }

    @Override
    public CertificateResponse updateCertificate(UUID id, UpdateCertificateRequest request) {
        Worker worker = findWorkerByCurrentUser();
        WorkerCertificate certificate = findCertificate(worker.getId(), id);

        if (request.title() != null) {
            certificate.setTitle(request.title().trim());
        }
        if (request.issuingOrg() != null) {
            certificate.setIssuingOrg(trimToNull(request.issuingOrg()));
        }
        if (request.issueDate() != null) {
            certificate.setIssueDate(request.issueDate());
        }
        if (request.expiryDate() != null) {
            certificate.setExpiryDate(request.expiryDate());
        }

        return portfolioMapper.toCertificateResponse(certificateRepository.save(certificate));
    }

    @Override
    public void deleteCertificate(UUID id) {
        Worker worker = findWorkerByCurrentUser();
        WorkerCertificate certificate = findCertificate(worker.getId(), id);
        certificate.setActive(false);
        certificateRepository.save(certificate);
        profileCompletionService.recalculateAndSave(worker);
    }

    @Override
    public IdentityDocumentResponse addIdentityDocument(AddIdentityDocumentRequest request) {
        Worker worker = findWorkerByCurrentUser();
        if (identityDocumentRepository.countByWorkerIdAndActiveTrue(worker.getId()) >= MAX_IDENTITY_DOCS) {
            throw new MaxPortfolioItemsExceededException("identity documents", MAX_IDENTITY_DOCS);
        }
        if (identityDocumentRepository.existsByWorkerIdAndDocumentTypeAndActiveTrue(worker.getId(), request.documentType())) {
            throw new IllegalStateException("Identity document of type " + request.documentType() + " already exists");
        }

        validateOwnedFile(worker, request.fileId(), FileCategory.IDENTITY_DOC);

        String encryptedNumber = encryptDocumentNumber(request.documentNumber());

        WorkerIdentityDocument document = WorkerIdentityDocument.builder()
                .worker(worker)
                .fileId(request.fileId())
                .documentType(request.documentType())
                .documentNumber(encryptedNumber)
                .build();

        WorkerIdentityDocument saved = identityDocumentRepository.save(document);
        profileCompletionService.recalculateAndSave(worker);
        return portfolioMapper.toIdentityDocumentResponse(
                saved,
                PortfolioMapper.maskDocumentNumber(request.documentNumber())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<IdentityDocumentResponse> getMyIdentityDocuments() {
        Worker worker = findWorkerByCurrentUser();
        return identityDocumentRepository.findByWorkerIdAndActiveTrueOrderByCreatedAtDesc(worker.getId()).stream()
                .map(document -> portfolioMapper.toIdentityDocumentResponse(
                        document,
                        PortfolioMapper.maskDocumentNumber(decryptDocumentNumber(document.getDocumentNumber()))
                ))
                .toList();
    }

    @Override
    public void deleteIdentityDocument(UUID id) {
        Worker worker = findWorkerByCurrentUser();
        WorkerIdentityDocument document = identityDocumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Identity document not found"));
        if (!document.getWorker().getId().equals(worker.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        document.setActive(false);
        identityDocumentRepository.save(document);
        profileCompletionService.recalculateAndSave(worker);
    }

    @Override
    public PortfolioSummaryResponse setProfilePhoto(SetProfilePhotoRequest request) {
        Worker worker = findWorkerByCurrentUser();
        validateOwnedFile(worker, request.fileId(), FileCategory.PROFILE_PHOTO);
        worker.setProfilePhotoFileId(request.fileId());
        workerRepository.save(worker);
        profileCompletionService.recalculateAndSave(worker);
        return buildSummary(worker);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicPortfolioResponse getPublicPortfolio(UUID workerId) {
        Worker worker = workerRepository.findById(workerId)
                .filter(w -> Boolean.TRUE.equals(w.getActive()))
                .orElseThrow(() -> new WorkerNotFoundException(workerId));

        List<PortfolioImageResponse> images = portfolioItemRepository
                .findByWorkerIdAndActiveTrueOrderByDisplayOrderAsc(worker.getId()).stream()
                .map(portfolioMapper::toImageResponse)
                .toList();

        List<CertificateResponse> certificates = certificateRepository
                .findByWorkerIdAndActiveTrueOrderByCreatedAtDesc(worker.getId()).stream()
                .map(portfolioMapper::toCertificateResponse)
                .toList();

        return portfolioMapper.toPublicPortfolioResponse(worker, images, certificates);
    }

    private PortfolioSummaryResponse buildSummary(Worker worker) {
        List<PortfolioImageResponse> images = portfolioItemRepository
                .findByWorkerIdAndActiveTrueOrderByDisplayOrderAsc(worker.getId()).stream()
                .map(portfolioMapper::toImageResponse)
                .toList();

        List<CertificateResponse> certificates = certificateRepository
                .findByWorkerIdAndActiveTrueOrderByCreatedAtDesc(worker.getId()).stream()
                .map(portfolioMapper::toCertificateResponse)
                .toList();

        return portfolioMapper.toSummaryResponse(worker, images, certificates);
    }

    private Worker findWorkerByCurrentUser() {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        return workerRepository.findByUserAccountId(currentUser.userAccountId())
                .orElseThrow(() -> new WorkerNotFoundException("Worker profile not found for current user"));
    }

    private WorkerPortfolioItem findPortfolioItem(UUID workerId, UUID id) {
        return portfolioItemRepository.findByIdAndWorkerIdAndActiveTrue(id, workerId)
                .orElseThrow(() -> new PortfolioItemNotFoundException(id));
    }

    private WorkerCertificate findCertificate(UUID workerId, UUID id) {
        return certificateRepository.findByIdAndWorkerIdAndActiveTrue(id, workerId)
                .orElseThrow(() -> new PortfolioItemNotFoundException("Certificate not found with id: " + id));
    }

    private void validateOwnedFile(Worker worker, UUID fileId, FileCategory expectedCategory) {
        StoredFile storedFile = storedFileRepository.findByIdAndActiveTrue(fileId)
                .orElseThrow(() -> new FileNotFoundException(fileId));

        UUID ownerUserId = worker.getUserAccount() != null ? worker.getUserAccount().getId() : null;
        if (ownerUserId != null && !storedFile.getOwnerUserId().equals(ownerUserId)) {
            throw new UnauthorizedException("You do not have access to this file");
        }
        if (ownerUserId == null) {
            throw new UnauthorizedException("You do not have access to this file");
        }
        if (storedFile.getFileCategory() != expectedCategory) {
            throw new FileNotFoundException(fileId);
        }
    }

    private String encryptDocumentNumber(String documentNumber) {
        if (documentNumber == null || documentNumber.isBlank()) {
            return null;
        }
        return documentEncryptionService.encrypt(documentNumber.trim());
    }

    private String decryptDocumentNumber(String encryptedNumber) {
        if (encryptedNumber == null || encryptedNumber.isBlank()) {
            return null;
        }
        return documentEncryptionService.decrypt(encryptedNumber);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
