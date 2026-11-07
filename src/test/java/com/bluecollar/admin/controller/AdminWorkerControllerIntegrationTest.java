package com.bluecollar.admin.controller;

import com.bluecollar.admin.dto.LinkWorkerUserRequest;
import com.bluecollar.admin.dto.RejectDocumentRequest;
import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.common.TestDbCleanup;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.portfolio.entity.DocumentType;
import com.bluecollar.portfolio.entity.VerificationStatus;
import com.bluecollar.portfolio.entity.WorkerIdentityDocument;
import com.bluecollar.portfolio.repository.WorkerIdentityDocumentRepository;
import com.bluecollar.storage.entity.FileCategory;
import com.bluecollar.storage.entity.StoredFile;
import com.bluecollar.storage.repository.StoredFileRepository;
import com.bluecollar.worker.entity.Gender;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminWorkerControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private WorkerIdentityDocumentRepository workerIdentityDocumentRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private TestDbCleanup testDbCleanup;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Worker worker;
    private Category category;
    private UserAccount adminUserAccount;
    private UsernamePasswordAuthenticationToken adminAuth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        testDbCleanup.cleanCoreDomain();

        adminUserAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("admin.reviewer@example.com")
                .phoneNumber("+919876543999")
                .passwordHash("hashed_password")
                .role(UserRole.ADMIN)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        AuthenticatedUser adminPrincipal = new AuthenticatedUser(
                adminUserAccount.getId(),
                adminUserAccount.getEmail(),
                UserRole.ADMIN
        );
        adminAuth = new UsernamePasswordAuthenticationToken(
                adminPrincipal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        category = categoryRepository.saveAndFlush(Category.builder()
                .name("Plumbing")
                .description("Pipe services")
                .active(true)
                .build());

        UserAccount userAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("worker.admin@example.com")
                .phoneNumber("+919876543250")
                .passwordHash("hashed_password")
                .role(UserRole.WORKER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(false)
                .build());

        worker = workerRepository.saveAndFlush(Worker.builder()
                .userAccount(userAccount)
                .firstName("Jane")
                .lastName("Smith")
                .phoneNumber("+919876543251")
                .email("jane.smith@example.com")
                .gender(Gender.FEMALE)
                .dateOfBirth(LocalDate.of(1992, 5, 10))
                .experienceYears(4)
                .bio("Skilled plumber")
                .hourlyRate(new BigDecimal("45.00"))
                .available(true)
                .verified(false)
                .active(true)
                .category(category)
                .averageRating(BigDecimal.ZERO)
                .reviewCount(0)
                .build());
    }

    private StoredFile saveStoredFile() {
        return storedFileRepository.saveAndFlush(StoredFile.builder()
                .ownerUserId(adminUserAccount.getId())
                .entityType(com.bluecollar.storage.entity.EntityType.IDENTITY_DOC)
                .fileCategory(FileCategory.IDENTITY_DOC)
                .originalName("id_doc.pdf")
                .storageKey("identity/id_doc.pdf")
                .sizeBytes(2048L)
                .mimeType("application/pdf")
                .active(true)
                .build());
    }

    private WorkerIdentityDocument saveDocument(Worker targetWorker, VerificationStatus status) {
        StoredFile file = saveStoredFile();
        return workerIdentityDocumentRepository.saveAndFlush(WorkerIdentityDocument.builder()
                .worker(targetWorker)
                .fileId(file.getId())
                .documentType(DocumentType.AADHAAR)
                .documentNumber("1234-5678-9012")
                .verificationStatus(status)
                .active(true)
                .build());
    }

    // --- Worker verification gating tests ---

    @Test
    void verifyWorkerShouldReturnConflictWhenIdentityDocumentIsMissing() throws Exception {
        mockMvc.perform(put("/api/v1/admin/workers/{id}/verify", worker.getId())
                        .with(authentication(adminAuth)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Worker must have at least one verified identity document"));
    }

    @Test
    void verifyWorkerShouldReturnConflictWhenDocumentIsPending() throws Exception {
        saveDocument(worker, VerificationStatus.PENDING);

        mockMvc.perform(put("/api/v1/admin/workers/{id}/verify", worker.getId())
                        .with(authentication(adminAuth)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot verify worker with pending identity documents"));
    }

    @Test
    void verifyWorkerShouldReturnConflictWhenDocumentIsRejected() throws Exception {
        saveDocument(worker, VerificationStatus.REJECTED);

        mockMvc.perform(put("/api/v1/admin/workers/{id}/verify", worker.getId())
                        .with(authentication(adminAuth)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot verify worker with rejected identity documents"));
    }

    @Test
    void verifyWorkerShouldSucceedWhenAllDocumentsAreVerified() throws Exception {
        saveDocument(worker, VerificationStatus.VERIFIED);

        mockMvc.perform(put("/api/v1/admin/workers/{id}/verify", worker.getId())
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Worker verified successfully"))
                .andExpect(jsonPath("$.data.verified").value(true));

        Worker updated = workerRepository.findById(worker.getId()).orElseThrow();
        assertTrue(updated.getVerified());
    }

    @Test
    void unverifyWorkerShouldUnverifyWorkerForAdmin() throws Exception {
        worker.setVerified(true);
        workerRepository.saveAndFlush(worker);

        mockMvc.perform(put("/api/v1/admin/workers/{id}/unverify", worker.getId())
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Worker unverified successfully"))
                .andExpect(jsonPath("$.data.verified").value(false));
    }

    @Test
    void deactivateWorkerShouldDeactivateWorkerForAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/admin/workers/{id}/deactivate", worker.getId())
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Worker deactivated successfully"))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.available").value(false));
    }

    // --- Document listing tests ---

    @Test
    void listPendingDocumentsShouldReturnDocumentsForAdmin() throws Exception {
        saveDocument(worker, VerificationStatus.PENDING);

        mockMvc.perform(get("/api/v1/admin/workers/documents/pending")
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].verificationStatus").value("PENDING"));
    }

    @Test
    void getWorkerDocumentsShouldReturnDocumentsForWorker() throws Exception {
        saveDocument(worker, VerificationStatus.PENDING);
        saveDocument(worker, VerificationStatus.VERIFIED);

        mockMvc.perform(get("/api/v1/admin/workers/{id}/documents", worker.getId())
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    // --- Document approval tests ---

    @Test
    void approveDocumentShouldApprovePendingDocumentAndRecordReviewer() throws Exception {
        WorkerIdentityDocument doc = saveDocument(worker, VerificationStatus.PENDING);

        mockMvc.perform(put("/api/v1/admin/workers/documents/{documentId}/approve", doc.getId())
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.verifiedBy").value(adminUserAccount.getId().toString()))
                .andExpect(jsonPath("$.data.verifiedAt").isNotEmpty());

        WorkerIdentityDocument updated = workerIdentityDocumentRepository.findById(doc.getId()).orElseThrow();
        assertEquals(VerificationStatus.VERIFIED, updated.getVerificationStatus());
        assertEquals(adminUserAccount.getId(), updated.getVerifiedBy());
        assertNotNull(updated.getVerifiedAt());
        assertNull(updated.getRejectionReason());
    }

    @Test
    void approveDocumentShouldReturnConflictWhenAlreadyVerified() throws Exception {
        WorkerIdentityDocument doc = saveDocument(worker, VerificationStatus.VERIFIED);

        mockMvc.perform(put("/api/v1/admin/workers/documents/{documentId}/approve", doc.getId())
                        .with(authentication(adminAuth)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Only pending identity documents can be approved, current status: VERIFIED"));
    }

    // --- Document rejection tests ---

    @Test
    void rejectDocumentShouldRejectPendingDocumentWithReasonAndRecordReviewer() throws Exception {
        WorkerIdentityDocument doc = saveDocument(worker, VerificationStatus.PENDING);
        RejectDocumentRequest request = new RejectDocumentRequest("Document photo is blurry and illegible");

        mockMvc.perform(put("/api/v1/admin/workers/documents/{documentId}/reject", doc.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verificationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.verifiedBy").value(adminUserAccount.getId().toString()))
                .andExpect(jsonPath("$.data.rejectionReason").value("Document photo is blurry and illegible"))
                .andExpect(jsonPath("$.data.verifiedAt").isNotEmpty());

        WorkerIdentityDocument updated = workerIdentityDocumentRepository.findById(doc.getId()).orElseThrow();
        assertEquals(VerificationStatus.REJECTED, updated.getVerificationStatus());
        assertEquals("Document photo is blurry and illegible", updated.getRejectionReason());
        assertEquals(adminUserAccount.getId(), updated.getVerifiedBy());
    }

    @Test
    void rejectDocumentShouldReturnBadRequestWhenReasonIsBlank() throws Exception {
        WorkerIdentityDocument doc = saveDocument(worker, VerificationStatus.PENDING);
        RejectDocumentRequest request = new RejectDocumentRequest("   ");

        mockMvc.perform(put("/api/v1/admin/workers/documents/{documentId}/reject", doc.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectDocumentShouldReturnConflictWhenAlreadyRejected() throws Exception {
        WorkerIdentityDocument doc = saveDocument(worker, VerificationStatus.REJECTED);
        RejectDocumentRequest request = new RejectDocumentRequest("Reason");

        mockMvc.perform(put("/api/v1/admin/workers/documents/{documentId}/reject", doc.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Only pending identity documents can be rejected, current status: REJECTED"));
    }

    // --- Worker account linking tests ---

    @Test
    void linkUserAccountShouldLinkWorkerWhenEmailMatches() throws Exception {
        // Create unlinked worker
        Worker unlinkedWorker = workerRepository.saveAndFlush(Worker.builder()
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+919876543301")
                .email("john.doe@example.com")
                .gender(Gender.MALE)
                .category(category)
                .hourlyRate(new BigDecimal("50.00"))
                .active(true)
                .build());

        // Create user account with matching email
        UserAccount customerUser = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("john.doe@example.com")
                .phoneNumber("+919876543302")
                .passwordHash("hashed_password")
                .role(UserRole.CUSTOMER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(false)
                .build());

        LinkWorkerUserRequest request = new LinkWorkerUserRequest(customerUser.getId());

        mockMvc.perform(put("/api/v1/admin/workers/{id}/link-user", unlinkedWorker.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(unlinkedWorker.getId().toString()));

        Worker updated = workerRepository.findById(unlinkedWorker.getId()).orElseThrow();
        assertNotNull(updated.getUserAccount());
        assertEquals(customerUser.getId(), updated.getUserAccount().getId());

        UserAccount updatedUser = userAccountRepository.findById(customerUser.getId()).orElseThrow();
        assertEquals(UserRole.WORKER, updatedUser.getRole());
    }

    @Test
    void linkUserAccountShouldReturnConflictWhenWorkerAlreadyLinked() throws Exception {
        UserAccount newUser = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("jane.smith@example.com")
                .phoneNumber("+919876543310")
                .passwordHash("hashed_password")
                .role(UserRole.CUSTOMER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(false)
                .build());

        LinkWorkerUserRequest request = new LinkWorkerUserRequest(newUser.getId());

        mockMvc.perform(put("/api/v1/admin/workers/{id}/link-user", worker.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Worker is already linked to a user account"));
    }

    @Test
    void linkUserAccountShouldReturnConflictWhenUserIsUnrelated() throws Exception {
        Worker unlinkedWorker = workerRepository.saveAndFlush(Worker.builder()
                .firstName("Bob")
                .lastName("Taylor")
                .phoneNumber("+919876543320")
                .email("bob.taylor@example.com")
                .gender(Gender.MALE)
                .category(category)
                .hourlyRate(new BigDecimal("50.00"))
                .active(true)
                .build());

        UserAccount unrelatedUser = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("someone.else@example.com")
                .phoneNumber("+919876543321")
                .passwordHash("hashed_password")
                .role(UserRole.CUSTOMER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(false)
                .build());

        LinkWorkerUserRequest request = new LinkWorkerUserRequest(unrelatedUser.getId());

        mockMvc.perform(put("/api/v1/admin/workers/{id}/link-user", unlinkedWorker.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot link worker to unrelated user account: email or phone number must match"));
    }

    @Test
    void linkUserAccountShouldReturnConflictWhenUserIsAdmin() throws Exception {
        Worker unlinkedWorker = workerRepository.saveAndFlush(Worker.builder()
                .firstName("Admin")
                .lastName("Worker")
                .phoneNumber("+919876543999")
                .email("admin.reviewer@example.com")
                .gender(Gender.MALE)
                .category(category)
                .hourlyRate(new BigDecimal("50.00"))
                .active(true)
                .build());

        LinkWorkerUserRequest request = new LinkWorkerUserRequest(adminUserAccount.getId());

        mockMvc.perform(put("/api/v1/admin/workers/{id}/link-user", unlinkedWorker.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot link an administrator account to a worker profile"));
    }

    // --- Authorization / Role access tests ---

    @Test
    void unverifyWorkerShouldReturnForbiddenForUnauthenticatedUser() throws Exception {
        mockMvc.perform(put("/api/v1/admin/workers/{id}/unverify", worker.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WORKER")
    void deactivateWorkerShouldReturnForbiddenForNonAdminUser() throws Exception {
        mockMvc.perform(put("/api/v1/admin/workers/{id}/deactivate", worker.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void listPendingDocumentsShouldReturnForbiddenForCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/admin/workers/documents/pending"))
                .andExpect(status().isForbidden());
    }
}
