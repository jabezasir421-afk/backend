package com.bluecollar.portfolio.service;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.common.exception.UnauthorizedException;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.common.service.DocumentEncryptionService;
import com.bluecollar.portfolio.dto.*;
import com.bluecollar.portfolio.entity.*;
import com.bluecollar.portfolio.exception.MaxPortfolioItemsExceededException;
import com.bluecollar.portfolio.exception.PortfolioItemNotFoundException;
import com.bluecollar.portfolio.mapper.PortfolioMapper;
import com.bluecollar.portfolio.repository.WorkerCertificateRepository;
import com.bluecollar.portfolio.repository.WorkerIdentityDocumentRepository;
import com.bluecollar.portfolio.repository.WorkerPortfolioItemRepository;
import com.bluecollar.storage.entity.EntityType;
import com.bluecollar.storage.entity.FileCategory;
import com.bluecollar.storage.entity.StoredFile;
import com.bluecollar.storage.exception.FileNotFoundException;
import com.bluecollar.storage.repository.StoredFileRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.exception.WorkerNotFoundException;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private WorkerPortfolioItemRepository portfolioItemRepository;

    @Mock
    private WorkerCertificateRepository certificateRepository;

    @Mock
    private WorkerIdentityDocumentRepository identityDocumentRepository;

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private PortfolioMapper portfolioMapper;

    @Mock
    private ProfileCompletionService profileCompletionService;

    @Mock
    private DocumentEncryptionService documentEncryptionService;

    @InjectMocks
    private PortfolioServiceImpl portfolioService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private UUID workerId;
    private UUID userAccountId;
    private UUID fileId;
    private UUID portfolioItemId;
    private UUID certificateId;
    private UserAccount userAccount;
    private Worker worker;
    private StoredFile storedFile;
    private PortfolioImageResponse imageResponse;
    private CertificateResponse certificateResponse;
    private ProfileCompletionResponse profileCompletionResponse;

    @BeforeEach
    void setUp() {
        workerId = UUID.randomUUID();
        userAccountId = UUID.randomUUID();
        fileId = UUID.randomUUID();
        portfolioItemId = UUID.randomUUID();
        certificateId = UUID.randomUUID();

        userAccount = UserAccount.builder()
                .email("worker@example.com")
                .role(UserRole.WORKER)
                .build();
        userAccount.setId(userAccountId);

        worker = Worker.builder()
                .userAccount(userAccount)
                .firstName("Bob")
                .lastName("Builder")
                .build();
        worker.setId(workerId);

        storedFile = StoredFile.builder()
                .ownerUserId(userAccountId)
                .entityType(EntityType.WORKER)
                .entityId(workerId)
                .fileCategory(FileCategory.PORTFOLIO_IMAGE)
                .storageKey("portfolio/test.jpg")
                .originalName("test.jpg")
                .mimeType("image/jpeg")
                .sizeBytes(1024L)
                .active(true)
                .build();
        storedFile.setId(fileId);

        imageResponse = new PortfolioImageResponse(
                portfolioItemId, fileId, "http://files/test.jpg", "Project", "Description", (short) 0, Instant.now()
        );
        certificateResponse = new CertificateResponse(
                certificateId, fileId, "Safety Cert", "OSHA",
                LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1),
                VerificationStatus.PENDING, Instant.now()
        );
        profileCompletionResponse = new ProfileCompletionResponse(
                45, Map.of("portfolio", false), List.of("portfolioImages")
        );

        securityUtilsMock = Mockito.mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUser)
                .thenReturn(new AuthenticatedUser(userAccountId, "worker@example.com", UserRole.WORKER));
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    void addPortfolioImageShouldSaveAndReturnResponseWhenValid() {
        AddPortfolioImageRequest request = new AddPortfolioImageRequest(fileId, "Project", "Description", (short) 1);

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(portfolioItemRepository.countByWorkerIdAndActiveTrue(workerId)).thenReturn(0L);
        when(storedFileRepository.findByIdAndActiveTrue(fileId)).thenReturn(Optional.of(storedFile));
        when(portfolioItemRepository.save(any(WorkerPortfolioItem.class))).thenAnswer(invocation -> {
            WorkerPortfolioItem item = invocation.getArgument(0);
            item.setId(portfolioItemId);
            return item;
        });
        when(portfolioMapper.toImageResponse(any(WorkerPortfolioItem.class))).thenReturn(imageResponse);

        PortfolioImageResponse result = portfolioService.addPortfolioImage(request);

        assertEquals(imageResponse, result);
        verify(portfolioItemRepository).save(any(WorkerPortfolioItem.class));
        verify(profileCompletionService).recalculateAndSave(worker);
    }

    @Test
    void addPortfolioImageShouldThrowWhenMaxLimitExceeded() {
        AddPortfolioImageRequest request = new AddPortfolioImageRequest(fileId, "Project", null, null);

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(portfolioItemRepository.countByWorkerIdAndActiveTrue(workerId)).thenReturn(20L);

        assertThrows(MaxPortfolioItemsExceededException.class, () -> portfolioService.addPortfolioImage(request));
        verify(portfolioItemRepository, never()).save(any());
    }

    @Test
    void addPortfolioImageShouldThrowWhenFileNotFound() {
        AddPortfolioImageRequest request = new AddPortfolioImageRequest(fileId, null, null, null);

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(portfolioItemRepository.countByWorkerIdAndActiveTrue(workerId)).thenReturn(0L);
        when(storedFileRepository.findByIdAndActiveTrue(fileId)).thenReturn(Optional.empty());

        assertThrows(FileNotFoundException.class, () -> portfolioService.addPortfolioImage(request));
        verify(portfolioItemRepository, never()).save(any());
    }

    @Test
    void addPortfolioImageShouldThrowWhenFileNotOwnedByWorker() {
        AddPortfolioImageRequest request = new AddPortfolioImageRequest(fileId, null, null, null);
        StoredFile otherUserFile = StoredFile.builder()
                .ownerUserId(UUID.randomUUID())
                .fileCategory(FileCategory.PORTFOLIO_IMAGE)
                .active(true)
                .build();
        otherUserFile.setId(fileId);

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(portfolioItemRepository.countByWorkerIdAndActiveTrue(workerId)).thenReturn(0L);
        when(storedFileRepository.findByIdAndActiveTrue(fileId)).thenReturn(Optional.of(otherUserFile));

        assertThrows(UnauthorizedException.class, () -> portfolioService.addPortfolioImage(request));
        verify(portfolioItemRepository, never()).save(any());
    }

    @Test
    void addCertificateShouldSaveAndReturnResponseWhenValid() {
        AddCertificateRequest request = new AddCertificateRequest(
                fileId, "Safety Cert", "OSHA", LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1)
        );
        StoredFile certificateFile = StoredFile.builder()
                .ownerUserId(userAccountId)
                .fileCategory(FileCategory.CERTIFICATE)
                .active(true)
                .build();
        certificateFile.setId(fileId);

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(certificateRepository.countByWorkerIdAndActiveTrue(workerId)).thenReturn(0L);
        when(storedFileRepository.findByIdAndActiveTrue(fileId)).thenReturn(Optional.of(certificateFile));
        when(certificateRepository.save(any(WorkerCertificate.class))).thenAnswer(invocation -> {
            WorkerCertificate certificate = invocation.getArgument(0);
            certificate.setId(certificateId);
            return certificate;
        });
        when(portfolioMapper.toCertificateResponse(any(WorkerCertificate.class))).thenReturn(certificateResponse);

        CertificateResponse result = portfolioService.addCertificate(request);

        assertEquals(certificateResponse, result);
        verify(certificateRepository).save(any(WorkerCertificate.class));
        verify(profileCompletionService).recalculateAndSave(worker);
    }

    @Test
    void addCertificateShouldThrowWhenMaxLimitExceeded() {
        AddCertificateRequest request = new AddCertificateRequest(fileId, "Safety Cert", null, null, null);

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(certificateRepository.countByWorkerIdAndActiveTrue(workerId)).thenReturn(10L);

        assertThrows(MaxPortfolioItemsExceededException.class, () -> portfolioService.addCertificate(request));
        verify(certificateRepository, never()).save(any());
    }

    @Test
    void addIdentityDocumentShouldSaveAndReturnResponseWhenValid() {
        AddIdentityDocumentRequest request = new AddIdentityDocumentRequest(
                fileId, DocumentType.PAN, "ABCDE1234F"
        );
        StoredFile identityFile = StoredFile.builder()
                .ownerUserId(userAccountId)
                .fileCategory(FileCategory.IDENTITY_DOC)
                .active(true)
                .build();
        identityFile.setId(fileId);
        IdentityDocumentResponse identityResponse = new IdentityDocumentResponse(
                UUID.randomUUID(), DocumentType.PAN, VerificationStatus.PENDING, "XXXXX234F", Instant.now()
        );

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(identityDocumentRepository.countByWorkerIdAndActiveTrue(workerId)).thenReturn(0L);
        when(identityDocumentRepository.existsByWorkerIdAndDocumentTypeAndActiveTrue(workerId, DocumentType.PAN))
                .thenReturn(false);
        when(storedFileRepository.findByIdAndActiveTrue(fileId)).thenReturn(Optional.of(identityFile));
        when(documentEncryptionService.encrypt("ABCDE1234F")).thenReturn("encrypted-value");
        when(identityDocumentRepository.save(any(WorkerIdentityDocument.class))).thenAnswer(invocation -> {
            WorkerIdentityDocument document = invocation.getArgument(0);
            document.setId(UUID.randomUUID());
            return document;
        });
        when(portfolioMapper.toIdentityDocumentResponse(any(WorkerIdentityDocument.class), any()))
                .thenReturn(identityResponse);

        IdentityDocumentResponse result = portfolioService.addIdentityDocument(request);

        assertEquals(identityResponse, result);
        verify(identityDocumentRepository).save(any(WorkerIdentityDocument.class));
        verify(profileCompletionService).recalculateAndSave(worker);
    }

    @Test
    void addIdentityDocumentShouldThrowWhenMaxLimitExceeded() {
        AddIdentityDocumentRequest request = new AddIdentityDocumentRequest(fileId, DocumentType.PAN, "ABCDE1234F");

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(identityDocumentRepository.countByWorkerIdAndActiveTrue(workerId)).thenReturn(3L);

        assertThrows(MaxPortfolioItemsExceededException.class, () -> portfolioService.addIdentityDocument(request));
        verify(identityDocumentRepository, never()).save(any());
    }

    @Test
    void addIdentityDocumentShouldThrowWhenDuplicateDocumentTypeExists() {
        AddIdentityDocumentRequest request = new AddIdentityDocumentRequest(fileId, DocumentType.AADHAAR, "1234");

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(identityDocumentRepository.countByWorkerIdAndActiveTrue(workerId)).thenReturn(0L);
        when(identityDocumentRepository.existsByWorkerIdAndDocumentTypeAndActiveTrue(workerId, DocumentType.AADHAAR))
                .thenReturn(true);

        assertThrows(IllegalStateException.class, () -> portfolioService.addIdentityDocument(request));
        verify(identityDocumentRepository, never()).save(any());
    }

    @Test
    void deletePortfolioImageShouldSoftDeleteAndRecalculateProfile() {
        WorkerPortfolioItem item = WorkerPortfolioItem.builder()
                .worker(worker)
                .fileId(fileId)
                .active(true)
                .build();
        item.setId(portfolioItemId);

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(portfolioItemRepository.findByIdAndWorkerIdAndActiveTrue(portfolioItemId, workerId))
                .thenReturn(Optional.of(item));

        portfolioService.deletePortfolioImage(portfolioItemId);

        verify(portfolioItemRepository).save(item);
        verify(profileCompletionService).recalculateAndSave(worker);
    }

    @Test
    void deletePortfolioImageShouldThrowWhenItemNotFound() {
        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(portfolioItemRepository.findByIdAndWorkerIdAndActiveTrue(portfolioItemId, workerId))
                .thenReturn(Optional.empty());

        assertThrows(PortfolioItemNotFoundException.class, () -> portfolioService.deletePortfolioImage(portfolioItemId));
        verify(portfolioItemRepository, never()).save(any());
    }

    @Test
    void deleteCertificateShouldSoftDeleteAndRecalculateProfile() {
        WorkerCertificate certificate = WorkerCertificate.builder()
                .worker(worker)
                .fileId(fileId)
                .title("Safety Cert")
                .active(true)
                .build();
        certificate.setId(certificateId);

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(certificateRepository.findByIdAndWorkerIdAndActiveTrue(certificateId, workerId))
                .thenReturn(Optional.of(certificate));

        portfolioService.deleteCertificate(certificateId);

        verify(certificateRepository).save(certificate);
        verify(profileCompletionService).recalculateAndSave(worker);
    }

    @Test
    void getProfileCompletionShouldReturnCalculatedResponse() {
        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(profileCompletionService.calculate(worker)).thenReturn(profileCompletionResponse);

        ProfileCompletionResponse result = portfolioService.getProfileCompletion();

        assertEquals(profileCompletionResponse, result);
        verify(profileCompletionService).calculate(worker);
    }

    @Test
    void getProfileCompletionShouldThrowWhenWorkerNotFoundForCurrentUser() {
        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.empty());

        assertThrows(WorkerNotFoundException.class, () -> portfolioService.getProfileCompletion());
        verify(profileCompletionService, never()).calculate(any());
    }
}
