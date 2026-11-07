package com.bluecollar.admin.controller;

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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminDocumentControllerIntegrationTest {

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

        Category category = categoryRepository.saveAndFlush(Category.builder()
                .name("Electrician")
                .description("Electrical services")
                .active(true)
                .build());

        UserAccount userAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("electrician@example.com")
                .phoneNumber("+919876543250")
                .passwordHash("hashed_password")
                .role(UserRole.WORKER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(false)
                .build());

        worker = workerRepository.saveAndFlush(Worker.builder()
                .userAccount(userAccount)
                .firstName("Bob")
                .lastName("Builder")
                .phoneNumber("+919876543251")
                .email("bob.builder@example.com")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .experienceYears(5)
                .bio("Master electrician")
                .hourlyRate(new BigDecimal("55.00"))
                .available(true)
                .verified(false)
                .active(true)
                .category(category)
                .averageRating(BigDecimal.ZERO)
                .reviewCount(0)
                .build());
    }

    private WorkerIdentityDocument saveDocument(VerificationStatus status) {
        StoredFile file = storedFileRepository.saveAndFlush(StoredFile.builder()
                .ownerUserId(adminUserAccount.getId())
                .entityType(com.bluecollar.storage.entity.EntityType.IDENTITY_DOC)
                .fileCategory(FileCategory.IDENTITY_DOC)
                .originalName("id_doc.pdf")
                .storageKey("identity/id_doc.pdf")
                .sizeBytes(2048L)
                .mimeType("application/pdf")
                .active(true)
                .build());

        return workerIdentityDocumentRepository.saveAndFlush(WorkerIdentityDocument.builder()
                .worker(worker)
                .fileId(file.getId())
                .documentType(DocumentType.PAN)
                .documentNumber("ABCDE1234F")
                .verificationStatus(status)
                .active(true)
                .build());
    }

    @Test
    void listPendingDocumentsShouldReturnListForAdmin() throws Exception {
        saveDocument(VerificationStatus.PENDING);

        mockMvc.perform(get("/api/v1/admin/documents/pending")
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].verificationStatus").value("PENDING"));
    }

    @Test
    void approveDocumentShouldSucceed() throws Exception {
        WorkerIdentityDocument doc = saveDocument(VerificationStatus.PENDING);

        mockMvc.perform(put("/api/v1/admin/documents/{documentId}/approve", doc.getId())
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.verifiedBy").value(adminUserAccount.getId().toString()));

        WorkerIdentityDocument updated = workerIdentityDocumentRepository.findById(doc.getId()).orElseThrow();
        assertEquals(VerificationStatus.VERIFIED, updated.getVerificationStatus());
    }

    @Test
    void rejectDocumentShouldSucceedWithReason() throws Exception {
        WorkerIdentityDocument doc = saveDocument(VerificationStatus.PENDING);
        RejectDocumentRequest request = new RejectDocumentRequest("Expired document");

        mockMvc.perform(put("/api/v1/admin/documents/{documentId}/reject", doc.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verificationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Expired document"));

        WorkerIdentityDocument updated = workerIdentityDocumentRepository.findById(doc.getId()).orElseThrow();
        assertEquals(VerificationStatus.REJECTED, updated.getVerificationStatus());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void pendingDocumentsShouldReturnForbiddenForCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/admin/documents/pending"))
                .andExpect(status().isForbidden());
    }
}
