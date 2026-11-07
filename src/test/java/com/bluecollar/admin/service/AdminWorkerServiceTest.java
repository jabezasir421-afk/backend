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
import com.bluecollar.portfolio.entity.DocumentType;
import com.bluecollar.portfolio.entity.VerificationStatus;
import com.bluecollar.portfolio.entity.WorkerIdentityDocument;
import com.bluecollar.portfolio.repository.WorkerIdentityDocumentRepository;
import com.bluecollar.portfolio.service.ProfileCompletionService;
import com.bluecollar.worker.dto.WorkerResponse;
import com.bluecollar.worker.entity.Gender;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.exception.WorkerNotFoundException;
import com.bluecollar.worker.mapper.WorkerMapper;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminWorkerServiceTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private WorkerMapper workerMapper;

    @Mock
    private WorkerIdentityDocumentRepository identityDocumentRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private DocumentEncryptionService documentEncryptionService;

    @Mock
    private ProfileCompletionService profileCompletionService;

    @Mock
    private com.bluecollar.audit.service.AuditLogService auditLogService;

    @InjectMocks
    private AdminWorkerServiceImpl adminWorkerService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private UUID workerId;
    private UUID adminUserId;
    private Worker worker;
    private WorkerResponse workerResponse;

    @BeforeEach
    void setUp() {
        workerId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();

        worker = Worker.builder()
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+1234567890")
                .email("john.doe@example.com")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .experienceYears(5)
                .bio("Experienced worker")
                .hourlyRate(new BigDecimal("50.00"))
                .available(true)
                .verified(false)
                .active(true)
                .averageRating(BigDecimal.ZERO)
                .reviewCount(0)
                .build();
        worker.setId(workerId);

        workerResponse = new WorkerResponse(
                workerId,
                "John",
                "Doe",
                "+1234567890",
                "john.doe@example.com",
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                5,
                "Experienced worker",
                new BigDecimal("50.00"),
                true,
                false,
                true,
                null,
                null,
                List.of(),
                BigDecimal.ZERO,
                0,
                Instant.now(),
                Instant.now()
        );

        securityUtilsMock = Mockito.mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUser)
                .thenReturn(new AuthenticatedUser(adminUserId, "admin@bluecollar.in", UserRole.ADMIN));
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    void verifyWorkerShouldThrowWhenNoDocumentsExist() {
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(identityDocumentRepository.findByWorkerIdAndActiveTrueOrderByCreatedAtDesc(workerId))
                .thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> adminWorkerService.verifyWorker(workerId));
    }

    @Test
    void verifyWorkerShouldThrowWhenIdentityDocumentIsPending() {
        WorkerIdentityDocument doc = WorkerIdentityDocument.builder()
                .worker(worker)
                .verificationStatus(VerificationStatus.PENDING)
                .build();

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(identityDocumentRepository.findByWorkerIdAndActiveTrueOrderByCreatedAtDesc(workerId))
                .thenReturn(List.of(doc));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> adminWorkerService.verifyWorker(workerId));
        assertTrue(ex.getMessage().contains("pending"));
    }

    @Test
    void verifyWorkerShouldThrowWhenIdentityDocumentIsRejected() {
        WorkerIdentityDocument doc = WorkerIdentityDocument.builder()
                .worker(worker)
                .verificationStatus(VerificationStatus.REJECTED)
                .build();

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(identityDocumentRepository.findByWorkerIdAndActiveTrueOrderByCreatedAtDesc(workerId))
                .thenReturn(List.of(doc));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> adminWorkerService.verifyWorker(workerId));
        assertTrue(ex.getMessage().contains("rejected"));
    }

    @Test
    void verifyWorkerShouldVerifyWorkerWhenAllDocumentsAreVerified() {
        WorkerIdentityDocument doc = WorkerIdentityDocument.builder()
                .worker(worker)
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(identityDocumentRepository.findByWorkerIdAndActiveTrueOrderByCreatedAtDesc(workerId))
                .thenReturn(List.of(doc));
        when(workerRepository.save(worker)).thenAnswer(invocation -> {
            Worker saved = invocation.getArgument(0);
            saved.setVerified(true);
            return saved;
        });
        when(workerMapper.toResponse(worker)).thenReturn(new WorkerResponse(
                workerId, "John", "Doe", "+1234567890", "john.doe@example.com",
                Gender.MALE, LocalDate.of(1990, 1, 1), 5, "Experienced worker",
                new BigDecimal("50.00"), true, true, true, null, null,
                List.of(), BigDecimal.ZERO, 0, Instant.now(), Instant.now()
        ));

        WorkerResponse result = adminWorkerService.verifyWorker(workerId);

        assertTrue(result.verified());
        verify(workerRepository).save(worker);
    }

    @Test
    void verifyWorkerShouldThrowWhenWorkerIsInactive() {
        worker.setActive(false);
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));

        assertThrows(IllegalStateException.class, () -> adminWorkerService.verifyWorker(workerId));
    }

    @Test
    void unverifyWorkerShouldSetVerifiedFalse() {
        worker.setVerified(true);
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(workerRepository.save(worker)).thenAnswer(invocation -> {
            Worker saved = invocation.getArgument(0);
            saved.setVerified(false);
            return saved;
        });
        when(workerMapper.toResponse(worker)).thenReturn(workerResponse);

        WorkerResponse result = adminWorkerService.unverifyWorker(workerId);

        assertFalse(result.verified());
        verify(workerRepository).save(worker);
    }

    @Test
    void deactivateWorkerShouldSetActiveAndAvailableFalse() {
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(workerRepository.save(worker)).thenAnswer(invocation -> {
            Worker saved = invocation.getArgument(0);
            saved.setActive(false);
            saved.setAvailable(false);
            return saved;
        });
        when(workerMapper.toResponse(worker)).thenReturn(new WorkerResponse(
                workerId, "John", "Doe", "+1234567890", "john.doe@example.com",
                Gender.MALE, LocalDate.of(1990, 1, 1), 5, "Experienced worker",
                new BigDecimal("50.00"), false, false, false, null, null,
                List.of(), BigDecimal.ZERO, 0, Instant.now(), Instant.now()
        ));

        WorkerResponse result = adminWorkerService.deactivateWorker(workerId);

        assertFalse(result.active());
        assertFalse(result.available());
        verify(workerRepository).save(worker);
    }

    @Test
    void verifyWorkerShouldThrowWhenWorkerDoesNotExist() {
        when(workerRepository.findById(workerId)).thenReturn(Optional.empty());

        assertThrows(WorkerNotFoundException.class, () -> adminWorkerService.verifyWorker(workerId));
    }

    // ─── Identity Document Approval & Rejection ─────────────────────────────────

    @Test
    void approveIdentityDocumentShouldApprovePendingDocument() {
        UUID docId = UUID.randomUUID();
        WorkerIdentityDocument doc = WorkerIdentityDocument.builder()
                .worker(worker)
                .documentType(DocumentType.PAN)
                .documentNumber("P1234567")
                .verificationStatus(VerificationStatus.PENDING)
                .build();
        doc.setId(docId);

        when(identityDocumentRepository.findByIdAndActiveTrue(docId)).thenReturn(Optional.of(doc));
        when(identityDocumentRepository.save(any(WorkerIdentityDocument.class))).thenAnswer(i -> i.getArgument(0));

        AdminIdentityDocumentResponse response = adminWorkerService.approveIdentityDocument(docId);

        assertEquals(VerificationStatus.VERIFIED, response.verificationStatus());
        assertEquals(adminUserId, response.verifiedBy());
        assertNotNull(response.verifiedAt());
        assertNull(response.rejectionReason());
        verify(profileCompletionService).recalculateAndSave(worker);
    }

    @Test
    void approveIdentityDocumentShouldThrowWhenNotPending() {
        UUID docId = UUID.randomUUID();
        WorkerIdentityDocument doc = WorkerIdentityDocument.builder()
                .worker(worker)
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();
        doc.setId(docId);

        when(identityDocumentRepository.findByIdAndActiveTrue(docId)).thenReturn(Optional.of(doc));

        assertThrows(IllegalStateException.class, () -> adminWorkerService.approveIdentityDocument(docId));
    }

    @Test
    void rejectIdentityDocumentShouldRejectPendingDocumentWithReason() {
        UUID docId = UUID.randomUUID();
        WorkerIdentityDocument doc = WorkerIdentityDocument.builder()
                .worker(worker)
                .documentType(DocumentType.AADHAAR)
                .documentNumber("123456789012")
                .verificationStatus(VerificationStatus.PENDING)
                .build();
        doc.setId(docId);

        when(identityDocumentRepository.findByIdAndActiveTrue(docId)).thenReturn(Optional.of(doc));
        when(identityDocumentRepository.save(any(WorkerIdentityDocument.class))).thenAnswer(i -> i.getArgument(0));

        RejectDocumentRequest req = new RejectDocumentRequest("Blurred image");
        AdminIdentityDocumentResponse response = adminWorkerService.rejectIdentityDocument(docId, req);

        assertEquals(VerificationStatus.REJECTED, response.verificationStatus());
        assertEquals(adminUserId, response.verifiedBy());
        assertEquals("Blurred image", response.rejectionReason());
        assertNotNull(response.verifiedAt());
        verify(profileCompletionService).recalculateAndSave(worker);
    }

    @Test
    void rejectIdentityDocumentShouldThrowWhenNotPending() {
        UUID docId = UUID.randomUUID();
        WorkerIdentityDocument doc = WorkerIdentityDocument.builder()
                .worker(worker)
                .verificationStatus(VerificationStatus.REJECTED)
                .build();
        doc.setId(docId);

        when(identityDocumentRepository.findByIdAndActiveTrue(docId)).thenReturn(Optional.of(doc));

        assertThrows(IllegalStateException.class,
                () -> adminWorkerService.rejectIdentityDocument(docId, new RejectDocumentRequest("Reason")));
    }

    @Test
    void listPendingIdentityDocumentsShouldReturnPagedDocuments() {
        WorkerIdentityDocument doc = WorkerIdentityDocument.builder()
                .worker(worker)
                .documentType(DocumentType.DRIVING_LICENSE)
                .verificationStatus(VerificationStatus.PENDING)
                .build();
        doc.setId(UUID.randomUUID());

        when(identityDocumentRepository.findByVerificationStatusAndActiveTrue(eq(VerificationStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(doc)));

        Page<AdminIdentityDocumentResponse> page = adminWorkerService.listPendingIdentityDocuments(PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
    }

    // ─── Worker Account Linking ────────────────────────────────────────────────

    @Test
    void linkUserAccountShouldLinkMatchingUser() {
        UUID userAccId = UUID.randomUUID();
        UserAccount userAccount = UserAccount.builder()
                .email("john.doe@example.com")
                .phoneNumber("+1234567890")
                .role(UserRole.CUSTOMER)
                .build();
        userAccount.setId(userAccId);

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(userAccountRepository.findById(userAccId)).thenReturn(Optional.of(userAccount));
        when(workerRepository.findByUserAccountId(userAccId)).thenReturn(Optional.empty());
        when(workerRepository.save(any(Worker.class))).thenAnswer(i -> i.getArgument(0));
        when(workerMapper.toResponse(any(Worker.class))).thenReturn(workerResponse);

        WorkerResponse result = adminWorkerService.linkUserAccount(workerId, new LinkWorkerUserRequest(userAccId));

        assertNotNull(result);
        assertEquals(UserRole.WORKER, userAccount.getRole());
        assertEquals(userAccount, worker.getUserAccount());
        verify(userAccountRepository).save(userAccount);
        verify(workerRepository).save(worker);
    }

    @Test
    void linkUserAccountShouldThrowWhenUnrelatedUser() {
        UUID userAccId = UUID.randomUUID();
        UserAccount unrelatedUser = UserAccount.builder()
                .email("someone.else@example.com")
                .phoneNumber("+9999999999")
                .role(UserRole.WORKER)
                .build();
        unrelatedUser.setId(userAccId);

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(userAccountRepository.findById(userAccId)).thenReturn(Optional.of(unrelatedUser));
        when(workerRepository.findByUserAccountId(userAccId)).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> adminWorkerService.linkUserAccount(workerId, new LinkWorkerUserRequest(userAccId)));
        assertTrue(ex.getMessage().contains("unrelated"));
    }

    @Test
    void linkUserAccountShouldThrowWhenWorkerAlreadyLinked() {
        worker.setUserAccount(new UserAccount());
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));

        assertThrows(IllegalStateException.class,
                () -> adminWorkerService.linkUserAccount(workerId, new LinkWorkerUserRequest(UUID.randomUUID())));
    }

    @Test
    void linkUserAccountShouldThrowWhenUserAlreadyLinkedToAnotherWorker() {
        UUID userAccId = UUID.randomUUID();
        UserAccount user = UserAccount.builder()
                .email("john.doe@example.com")
                .phoneNumber("+1234567890")
                .build();
        user.setId(userAccId);

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(userAccountRepository.findById(userAccId)).thenReturn(Optional.of(user));
        when(workerRepository.findByUserAccountId(userAccId)).thenReturn(Optional.of(new Worker()));

        assertThrows(IllegalStateException.class,
                () -> adminWorkerService.linkUserAccount(workerId, new LinkWorkerUserRequest(userAccId)));
    }
}
