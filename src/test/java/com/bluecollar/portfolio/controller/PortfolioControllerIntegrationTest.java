package com.bluecollar.portfolio.controller;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.portfolio.repository.WorkerCertificateRepository;
import com.bluecollar.portfolio.repository.WorkerIdentityDocumentRepository;
import com.bluecollar.portfolio.repository.WorkerPortfolioItemRepository;
import com.bluecollar.storage.entity.EntityType;
import com.bluecollar.storage.entity.FileCategory;
import com.bluecollar.storage.entity.StoredFile;
import com.bluecollar.storage.repository.StoredFileRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PortfolioControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private WorkerPortfolioItemRepository portfolioItemRepository;

    @Autowired
    private WorkerCertificateRepository certificateRepository;

    @Autowired
    private WorkerIdentityDocumentRepository identityDocumentRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private UserAccount workerUserAccount;
    private Worker worker;
    private Category category;
    private UsernamePasswordAuthenticationToken workerAuth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        portfolioItemRepository.deleteAll();
        certificateRepository.deleteAll();
        identityDocumentRepository.deleteAll();
        storedFileRepository.deleteAll();
        workerRepository.deleteAll();
        userAccountRepository.deleteAll();
        categoryRepository.deleteAll();

        workerUserAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("work@example.com")
                .phoneNumber("+1111111111")
                .passwordHash("password")
                .role(UserRole.WORKER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        category = categoryRepository.saveAndFlush(Category.builder()
                .name("Plumbing-PortfolioCtrl")
                .description("Pipe services")
                .active(true)
                .build());

        worker = workerRepository.saveAndFlush(Worker.builder()
                .userAccount(workerUserAccount)
                .firstName("Bob")
                .lastName("Builder")
                .phoneNumber("+0987654321")
                .email("worker@example.com")
                .category(category)
                .hourlyRate(BigDecimal.valueOf(50.00))
                .active(true)
                .verified(true)
                .available(true)
                .build());

        com.bluecollar.common.security.AuthenticatedUser authenticatedWorker = new com.bluecollar.common.security.AuthenticatedUser(
                workerUserAccount.getId(),
                workerUserAccount.getEmail(),
                UserRole.WORKER
        );
        workerAuth = new UsernamePasswordAuthenticationToken(
                authenticatedWorker,
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_WORKER"))
        );
    }

    @Test
    void getMyPortfolioShouldReturnPortfolioForWorker() throws Exception {
        StoredFile file = saveStoredFile(FileCategory.PORTFOLIO_IMAGE);
        portfolioItemRepository.saveAndFlush(com.bluecollar.portfolio.entity.WorkerPortfolioItem.builder()
                .worker(worker)
                .fileId(file.getId())
                .title("Kitchen remodel")
                .displayOrder((short) 0)
                .build());

        mockMvc.perform(get("/api/v1/workers/me/portfolio")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(workerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Portfolio fetched successfully"))
                .andExpect(jsonPath("$.data.images", hasSize(1)))
                .andExpect(jsonPath("$.data.images[0].title").value("Kitchen remodel"));
    }

    @Test
    void addPortfolioImageShouldCreateImageAndReturnApiResponse() throws Exception {
        StoredFile file = saveStoredFile(FileCategory.PORTFOLIO_IMAGE);
        String payload = """
                {
                  "fileId": "%s",
                  "title": "Bathroom tile",
                  "description": "Recent project",
                  "displayOrder": 1
                }
                """.formatted(file.getId());

        mockMvc.perform(post("/api/v1/workers/me/portfolio/images")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(workerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Portfolio image added successfully"))
                .andExpect(jsonPath("$.data.title").value("Bathroom tile"));
    }

    @Test
    void addPortfolioImageShouldReturnValidationErrorsForMissingFileId() throws Exception {
        String payload = """
                {
                  "title": "Missing file"
                }
                """;

        mockMvc.perform(post("/api/v1/workers/me/portfolio/images")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(workerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("fileId"));
    }

    @Test
    void addCertificateShouldReturnValidationErrorsForBlankTitle() throws Exception {
        StoredFile file = saveStoredFile(FileCategory.CERTIFICATE);
        String payload = """
                {
                  "fileId": "%s",
                  "title": ""
                }
                """.formatted(file.getId());

        mockMvc.perform(post("/api/v1/workers/me/portfolio/certificates")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(workerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
    }

    @Test
    void addIdentityDocumentShouldCreateDocumentAndReturnApiResponse() throws Exception {
        StoredFile file = saveStoredFile(FileCategory.IDENTITY_DOC);
        String payload = """
                {
                  "fileId": "%s",
                  "documentType": "PAN"
                }
                """.formatted(file.getId());

        mockMvc.perform(post("/api/v1/workers/me/portfolio/identity-documents")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(workerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Identity document submitted successfully"))
                .andExpect(jsonPath("$.data.documentType").value("PAN"));
    }

    @Test
    void deletePortfolioImageShouldSoftDeleteExistingImage() throws Exception {
        StoredFile file = saveStoredFile(FileCategory.PORTFOLIO_IMAGE);
        com.bluecollar.portfolio.entity.WorkerPortfolioItem item = portfolioItemRepository.saveAndFlush(
                com.bluecollar.portfolio.entity.WorkerPortfolioItem.builder()
                        .worker(worker)
                        .fileId(file.getId())
                        .title("To delete")
                        .build()
        );

        mockMvc.perform(delete("/api/v1/workers/me/portfolio/images/{id}", item.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(workerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Portfolio image deleted successfully"));

        assertFalse(portfolioItemRepository.findByIdAndWorkerIdAndActiveTrue(item.getId(), worker.getId()).isPresent());
    }

    @Test
    void getProfileCompletionShouldReturnCompletionForWorker() throws Exception {
        mockMvc.perform(get("/api/v1/workers/me/portfolio/completion")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(workerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Profile completion fetched successfully"))
                .andExpect(jsonPath("$.data.completionPercent").isNumber())
                .andExpect(jsonPath("$.data.sections").isMap());
    }

    @Test
    void getPublicPortfolioShouldReturnActiveImagesWithoutAuth() throws Exception {
        StoredFile file = saveStoredFile(FileCategory.PORTFOLIO_IMAGE);
        portfolioItemRepository.saveAndFlush(com.bluecollar.portfolio.entity.WorkerPortfolioItem.builder()
                .worker(worker)
                .fileId(file.getId())
                .title("Public project")
                .displayOrder((short) 0)
                .build());

        mockMvc.perform(get("/api/v1/workers/{id}/portfolio", worker.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Public portfolio fetched successfully"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("Public project"));
    }

    @Test
    void getMyPortfolioShouldReturnForbiddenWithoutWorkerAuth() throws Exception {
        mockMvc.perform(get("/api/v1/workers/me/portfolio"))
                .andExpect(status().isForbidden());
    }

    private StoredFile saveStoredFile(FileCategory fileCategory) {
        return storedFileRepository.saveAndFlush(StoredFile.builder()
                .ownerUserId(workerUserAccount.getId())
                .entityType(EntityType.WORKER)
                .entityId(worker.getId())
                .fileCategory(fileCategory)
                .storageKey("test/" + UUID.randomUUID() + ".jpg")
                .originalName("test.jpg")
                .mimeType("image/jpeg")
                .sizeBytes(1024L)
                .active(true)
                .build());
    }
}
