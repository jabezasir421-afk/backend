package com.bluecollar.admin.service;

import com.bluecollar.admin.dto.AdminIdentityDocumentResponse;
import com.bluecollar.admin.dto.LinkWorkerUserRequest;
import com.bluecollar.admin.dto.RejectDocumentRequest;
import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.common.service.DocumentEncryptionService;
import com.bluecollar.portfolio.entity.VerificationStatus;
import com.bluecollar.portfolio.entity.WorkerIdentityDocument;
import com.bluecollar.portfolio.exception.IdentityDocumentNotFoundException;
import com.bluecollar.portfolio.mapper.PortfolioMapper;
import com.bluecollar.portfolio.repository.WorkerIdentityDocumentRepository;
import com.bluecollar.portfolio.service.ProfileCompletionService;
import com.bluecollar.worker.dto.WorkerResponse;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.exception.WorkerNotFoundException;
import com.bluecollar.worker.mapper.WorkerMapper;
import com.bluecollar.worker.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminWorkerServiceImpl implements AdminWorkerService {

    private final WorkerRepository workerRepository;
    private final WorkerMapper workerMapper;
    private final WorkerIdentityDocumentRepository identityDocumentRepository;
    private final UserAccountRepository userAccountRepository;
    private final DocumentEncryptionService documentEncryptionService;
    private final ProfileCompletionService profileCompletionService;

    @Override
    public WorkerResponse verifyWorker(UUID id) {
        Worker worker = findWorker(id);
        if (!Boolean.TRUE.equals(worker.getActive())) {
            throw new IllegalStateException("Cannot verify an inactive worker");
        }

        List<WorkerIdentityDocument> documents = identityDocumentRepository
                .findByWorkerIdAndActiveTrueOrderByCreatedAtDesc(worker.getId());
        if (documents.isEmpty()) {
            throw new IllegalStateException("Worker must have at least one verified identity document");
        }

        boolean hasPending = documents.stream()
                .anyMatch(d -> d.getVerificationStatus() == VerificationStatus.PENDING);
        if (hasPending) {
            throw new IllegalStateException("Cannot verify worker with pending identity documents");
        }

        boolean hasRejected = documents.stream()
                .anyMatch(d -> d.getVerificationStatus() == VerificationStatus.REJECTED);
        if (hasRejected) {
            throw new IllegalStateException("Cannot verify worker with rejected identity documents");
        }

        boolean allVerified = documents.stream()
                .allMatch(d -> d.getVerificationStatus() == VerificationStatus.VERIFIED);
        if (!allVerified) {
            throw new IllegalStateException("All required identity documents must be approved before worker verification");
        }

        worker.setVerified(true);
        return workerMapper.toResponse(workerRepository.save(worker));
    }

    @Override
    public WorkerResponse unverifyWorker(UUID id) {
        Worker worker = findWorker(id);
        worker.setVerified(false);
        return workerMapper.toResponse(workerRepository.save(worker));
    }

    @Override
    public WorkerResponse deactivateWorker(UUID id) {
        Worker worker = findWorker(id);
        worker.setActive(false);
        worker.setAvailable(false);
        return workerMapper.toResponse(workerRepository.save(worker));
    }

    @Override
    public WorkerResponse linkUserAccount(UUID workerId, LinkWorkerUserRequest request) {
        Worker worker = findWorker(workerId);
        if (worker.getUserAccount() != null) {
            throw new IllegalStateException("Worker is already linked to a user account");
        }

        UserAccount userAccount = userAccountRepository.findById(request.userAccountId())
                .orElseThrow(() -> new IllegalArgumentException("User account not found: " + request.userAccountId()));

        if (workerRepository.findByUserAccountId(userAccount.getId()).isPresent()) {
            throw new IllegalStateException("User account is already linked to another worker");
        }

        if (userAccount.getRole() == UserRole.ADMIN) {
            throw new IllegalStateException("Cannot link an administrator account to a worker profile");
        }

        boolean emailMatches = worker.getEmail() != null
                && userAccount.getEmail() != null
                && worker.getEmail().trim().equalsIgnoreCase(userAccount.getEmail().trim());

        boolean phoneMatches = worker.getPhoneNumber() != null
                && userAccount.getPhoneNumber() != null
                && worker.getPhoneNumber().trim().equals(userAccount.getPhoneNumber().trim());

        if (!emailMatches && !phoneMatches) {
            throw new IllegalStateException("Cannot link worker to unrelated user account: email or phone number must match");
        }

        if (userAccount.getRole() != UserRole.WORKER) {
            userAccount.setRole(UserRole.WORKER);
            userAccountRepository.save(userAccount);
        }

        worker.setUserAccount(userAccount);
        Worker saved = workerRepository.save(worker);
        return workerMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminIdentityDocumentResponse> listPendingIdentityDocuments(Pageable pageable) {
        return identityDocumentRepository.findByVerificationStatusAndActiveTrue(VerificationStatus.PENDING, pageable)
                .map(this::toAdminDocumentResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminIdentityDocumentResponse> getWorkerIdentityDocuments(UUID workerId) {
        findWorker(workerId); // ensure worker exists
        return identityDocumentRepository.findByWorkerIdAndActiveTrueOrderByCreatedAtDesc(workerId)
                .stream()
                .map(this::toAdminDocumentResponse)
                .toList();
    }

    @Override
    public AdminIdentityDocumentResponse approveIdentityDocument(UUID documentId) {
        WorkerIdentityDocument document = identityDocumentRepository.findByIdAndActiveTrue(documentId)
                .orElseThrow(() -> new IdentityDocumentNotFoundException(documentId));

        if (document.getVerificationStatus() != VerificationStatus.PENDING) {
            throw new IllegalStateException("Only pending identity documents can be approved, current status: " + document.getVerificationStatus());
        }

        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        document.setVerificationStatus(VerificationStatus.VERIFIED);
        document.setVerifiedBy(currentUser != null ? currentUser.userAccountId() : null);
        document.setVerifiedAt(Instant.now());
        document.setRejectionReason(null);

        WorkerIdentityDocument saved = identityDocumentRepository.save(document);
        profileCompletionService.recalculateAndSave(saved.getWorker());
        return toAdminDocumentResponse(saved);
    }

    @Override
    public AdminIdentityDocumentResponse rejectIdentityDocument(UUID documentId, RejectDocumentRequest request) {
        WorkerIdentityDocument document = identityDocumentRepository.findByIdAndActiveTrue(documentId)
                .orElseThrow(() -> new IdentityDocumentNotFoundException(documentId));

        if (document.getVerificationStatus() != VerificationStatus.PENDING) {
            throw new IllegalStateException("Only pending identity documents can be rejected, current status: " + document.getVerificationStatus());
        }

        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        document.setVerificationStatus(VerificationStatus.REJECTED);
        document.setVerifiedBy(currentUser != null ? currentUser.userAccountId() : null);
        document.setVerifiedAt(Instant.now());
        document.setRejectionReason(request != null && request.reason() != null ? request.reason().trim() : "Rejected by administrator");

        WorkerIdentityDocument saved = identityDocumentRepository.save(document);
        profileCompletionService.recalculateAndSave(saved.getWorker());
        return toAdminDocumentResponse(saved);
    }

    private Worker findWorker(UUID id) {
        return workerRepository.findById(id)
                .orElseThrow(() -> new WorkerNotFoundException(id));
    }

    private AdminIdentityDocumentResponse toAdminDocumentResponse(WorkerIdentityDocument document) {
        String workerName = document.getWorker() != null
                ? (document.getWorker().getFirstName() + " " + document.getWorker().getLastName()).trim()
                : null;
        return new AdminIdentityDocumentResponse(
                document.getId(),
                document.getWorker() != null ? document.getWorker().getId() : null,
                workerName,
                document.getFileId(),
                document.getDocumentType(),
                resolveMaskedNumber(document.getDocumentNumber()),
                document.getVerificationStatus(),
                document.getVerifiedBy(),
                document.getVerifiedAt(),
                document.getRejectionReason(),
                document.getCreatedAt()
        );
    }

    private String resolveMaskedNumber(String rawOrEncrypted) {
        if (rawOrEncrypted == null || rawOrEncrypted.isBlank()) {
            return null;
        }
        try {
            String decrypted = documentEncryptionService.decrypt(rawOrEncrypted);
            return PortfolioMapper.maskDocumentNumber(decrypted != null ? decrypted : rawOrEncrypted);
        } catch (Exception ex) {
            return PortfolioMapper.maskDocumentNumber(rawOrEncrypted);
        }
    }
}
