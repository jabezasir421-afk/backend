package com.bluecollar.security;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.portfolio.entity.WorkerPortfolioItem;
import com.bluecollar.portfolio.repository.WorkerCertificateRepository;
import com.bluecollar.portfolio.repository.WorkerIdentityDocumentRepository;
import com.bluecollar.portfolio.repository.WorkerPortfolioItemRepository;
import com.bluecollar.storage.entity.EntityType;
import com.bluecollar.storage.entity.FileCategory;
import com.bluecollar.storage.entity.StoredFile;
import com.bluecollar.storage.repository.StoredFileRepository;
import com.bluecollar.storage.service.FileStorageService;
import com.bluecollar.worker.entity.Gender;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PrivacyAndFileSecurityIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private WorkerPortfolioItemRepository portfolioItemRepository;

    @Autowired
    private WorkerCertificateRepository certificateRepository;

    @Autowired
    private WorkerIdentityDocumentRepository identityDocumentRepository;

    @Autowired
    private FileStorageService fileStorageService;

    private Category category;
    private Worker worker;
    private UserAccount workerUserAccount;
    private UsernamePasswordAuthenticationToken workerAuth;

    private Customer customer1;
    private UserAccount customer1UserAccount;
    private UsernamePasswordAuthenticationToken customer1Auth;

    private Customer customer2;
    private UserAccount customer2UserAccount;
    private UsernamePasswordAuthenticationToken customer2Auth;

    private UserAccount adminUserAccount;
    private UsernamePasswordAuthenticationToken adminAuth;

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
        customerRepository.deleteAll();
        userAccountRepository.deleteAll();
        categoryRepository.deleteAll();

        category = categoryRepository.saveAndFlush(Category.builder()
                .name("Electrical")
                .description("Electrical trade")
                .active(true)
                .build());

        workerUserAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("worker@test.com")
                .phoneNumber("+41790000001")
                .passwordHash("hash")
                .role(UserRole.WORKER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        worker = workerRepository.saveAndFlush(Worker.builder()
                .userAccount(workerUserAccount)
                .firstName("Hans")
                .lastName("Müller")
                .phoneNumber("+41790000001")
                .email("worker@test.com")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1985, 5, 20))
                .experienceYears(10)
                .bio("Master Electrician")
                .hourlyRate(new BigDecimal("95.00"))
                .available(true)
                .verified(true)
                .active(true)
                .category(category)
                .primaryCity("Zurich")
                .build());

        workerAuth = new UsernamePasswordAuthenticationToken(
                new com.bluecollar.common.security.AuthenticatedUser(workerUserAccount.getId(), workerUserAccount.getEmail(), UserRole.WORKER),
                null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_WORKER"))
        );

        customer1UserAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("cust1@test.com")
                .phoneNumber("+41790000002")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        customer1 = customerRepository.saveAndFlush(Customer.builder()
                .userAccount(customer1UserAccount)
                .firstName("Anna")
                .lastName("Schmidt")
                .active(true)
                .build());

        customer1Auth = new UsernamePasswordAuthenticationToken(
                new com.bluecollar.common.security.AuthenticatedUser(customer1UserAccount.getId(), customer1UserAccount.getEmail(), UserRole.CUSTOMER),
                null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );

        customer2UserAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("cust2@test.com")
                .phoneNumber("+41790000003")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        customer2 = customerRepository.saveAndFlush(Customer.builder()
                .userAccount(customer2UserAccount)
                .firstName("Beat")
                .lastName("Keller")
                .active(true)
                .build());

        customer2Auth = new UsernamePasswordAuthenticationToken(
                new com.bluecollar.common.security.AuthenticatedUser(customer2UserAccount.getId(), customer2UserAccount.getEmail(), UserRole.CUSTOMER),
                null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );

        adminUserAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("admin@test.com")
                .phoneNumber("+41790000000")
                .passwordHash("hash")
                .role(UserRole.ADMIN)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        adminAuth = new UsernamePasswordAuthenticationToken(
                new com.bluecollar.common.security.AuthenticatedUser(adminUserAccount.getId(), adminUserAccount.getEmail(), UserRole.ADMIN),
                null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Test 1: Anonymous users cannot see worker PII
    // ───────────────────────────────────────────────────────────────────────────
    @Test
    void anonymousUsersCannotSeeWorkerPii() throws Exception {
        mockMvc.perform(get("/api/v1/workers/{id}", worker.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(worker.getId().toString()))
                .andExpect(jsonPath("$.data.firstName").value("Hans"))
                .andExpect(jsonPath("$.data.lastName").value("Müller"))
                .andExpect(jsonPath("$.data.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.dateOfBirth").doesNotExist());

        mockMvc.perform(get("/api/v1/workers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].firstName").value("Hans"))
                .andExpect(jsonPath("$.data.content[0].phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].dateOfBirth").doesNotExist());
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Test 2: Customers cannot attach another user's file
    // ───────────────────────────────────────────────────────────────────────────
    @Test
    void customersCannotAttachAnotherUsersFile() throws Exception {
        // Customer 2 uploads their own file
        StoredFile cust2File = storedFileRepository.saveAndFlush(StoredFile.builder()
                .ownerUserId(customer2UserAccount.getId())
                .entityType(EntityType.CUSTOMER)
                .entityId(customer2.getId())
                .fileCategory(FileCategory.PROFILE_PHOTO)
                .storageKey("cust2/" + UUID.randomUUID() + "/photo.jpg")
                .originalName("photo.jpg")
                .mimeType("image/jpeg")
                .sizeBytes(1024L)
                .active(true)
                .build());

        // Customer 1 tries to assign Customer 2's file as their own profile photo
        String payload = """
                {
                  "fileId": "%s"
                }
                """.formatted(cust2File.getId());

        mockMvc.perform(put("/api/v1/customers/me/profile-photo")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(customer1Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Test 3: Customers cannot download other users' private files
    // ───────────────────────────────────────────────────────────────────────────
    @Test
    void customersCannotDownloadOtherUsersPrivateFiles() throws Exception {
        byte[] content = "private customer data".getBytes(StandardCharsets.UTF_8);
        String storageKey = "cust2/" + UUID.randomUUID() + "/photo.jpg";
        fileStorageService.store(storageKey, new ByteArrayInputStream(content), "image/jpeg", content.length);

        StoredFile cust2File = storedFileRepository.saveAndFlush(StoredFile.builder()
                .ownerUserId(customer2UserAccount.getId())
                .entityType(EntityType.CUSTOMER)
                .entityId(customer2.getId())
                .fileCategory(FileCategory.PROFILE_PHOTO)
                .storageKey(storageKey)
                .originalName("photo.jpg")
                .mimeType("image/jpeg")
                .sizeBytes((long) content.length)
                .active(true)
                .build());

        // Customer 1 attempts to download Customer 2's private file
        mockMvc.perform(get("/api/v1/files/{id}/download", cust2File.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(customer1Auth)))
                .andExpect(status().isForbidden());
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Test 4: Unauthorized users cannot retrieve certificates or identity documents
    // ───────────────────────────────────────────────────────────────────────────
    @Test
    void unauthorizedUsersCannotRetrieveCertificatesOrIdentityDocuments() throws Exception {
        byte[] certContent = "certificate bytes".getBytes(StandardCharsets.UTF_8);
        String certStorageKey = "worker/" + UUID.randomUUID() + "/cert.pdf";
        fileStorageService.store(certStorageKey, new ByteArrayInputStream(certContent), "application/pdf", certContent.length);

        StoredFile certFile = storedFileRepository.saveAndFlush(StoredFile.builder()
                .ownerUserId(workerUserAccount.getId())
                .entityType(EntityType.CERTIFICATE)
                .fileCategory(FileCategory.CERTIFICATE)
                .storageKey(certStorageKey)
                .originalName("cert.pdf")
                .mimeType("application/pdf")
                .sizeBytes((long) certContent.length)
                .active(true)
                .build());

        byte[] idContent = "id document bytes".getBytes(StandardCharsets.UTF_8);
        String idStorageKey = "worker/" + UUID.randomUUID() + "/id.pdf";
        fileStorageService.store(idStorageKey, new ByteArrayInputStream(idContent), "application/pdf", idContent.length);

        StoredFile idFile = storedFileRepository.saveAndFlush(StoredFile.builder()
                .ownerUserId(workerUserAccount.getId())
                .entityType(EntityType.IDENTITY_DOC)
                .fileCategory(FileCategory.IDENTITY_DOC)
                .storageKey(idStorageKey)
                .originalName("id.pdf")
                .mimeType("application/pdf")
                .sizeBytes((long) idContent.length)
                .active(true)
                .build());

        // 1. Customer cannot download worker's certificate
        mockMvc.perform(get("/api/v1/files/{id}/download", certFile.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(customer1Auth)))
                .andExpect(status().isForbidden());

        // 2. Customer cannot download worker's identity document
        mockMvc.perform(get("/api/v1/files/{id}/download", idFile.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(customer1Auth)))
                .andExpect(status().isForbidden());

        // 3. Worker cannot download identity document (strictly Admin only)
        mockMvc.perform(get("/api/v1/files/{id}/download", idFile.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(workerAuth)))
                .andExpect(status().isForbidden());

        // 4. Admin CAN download worker's certificate
        mockMvc.perform(get("/api/v1/files/{id}/download", certFile.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(adminAuth)))
                .andExpect(status().isOk());

        // 5. Admin CAN download worker's identity document
        mockMvc.perform(get("/api/v1/files/{id}/download", idFile.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(adminAuth)))
                .andExpect(status().isOk());

        // 6. Owning Worker CAN download their own certificate
        mockMvc.perform(get("/api/v1/files/{id}/download", certFile.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(workerAuth)))
                .andExpect(status().isOk());
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Test 5: Public portfolio images load through a valid HTTP URL
    // ───────────────────────────────────────────────────────────────────────────
    @Test
    void publicPortfolioImagesLoadThroughValidHttpUrl() throws Exception {
        byte[] imgBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01};
        String storageKey = workerUserAccount.getId() + "/portfolio_image/" + UUID.randomUUID() + "/project.jpg";
        fileStorageService.store(storageKey, new ByteArrayInputStream(imgBytes), "image/jpeg", imgBytes.length);

        StoredFile storedFile = storedFileRepository.saveAndFlush(StoredFile.builder()
                .ownerUserId(workerUserAccount.getId())
                .entityType(EntityType.PORTFOLIO)
                .entityId(worker.getId())
                .fileCategory(FileCategory.PORTFOLIO_IMAGE)
                .storageKey(storageKey)
                .originalName("project.jpg")
                .mimeType("image/jpeg")
                .sizeBytes((long) imgBytes.length)
                .active(true)
                .build());

        portfolioItemRepository.saveAndFlush(WorkerPortfolioItem.builder()
                .worker(worker)
                .fileId(storedFile.getId())
                .title("Modern Electrical Panel")
                .displayOrder((short) 1)
                .build());

        // Anonymous user fetches public portfolio
        MvcResult result = mockMvc.perform(get("/api/v1/workers/{id}/portfolio", worker.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.images", hasSize(1)))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String imageUrl = JsonPath.read(responseBody, "$.data.images[0].imageUrl");

        // Verify URL validity and path disclosure prevention
        assertTrue(imageUrl.startsWith("http://") || imageUrl.startsWith("https://"), "Image URL must be HTTP(S)");
        assertFalse(imageUrl.startsWith("file://"), "Image URL must not use file:// scheme");
        assertFalse(imageUrl.contains("/var/bluecollar/files"), "Image URL must not disclose internal filesystem paths");

        // Anonymous user loads image content through the valid HTTP URL
        URI uri = URI.create(imageUrl);
        String relativePath = uri.getRawPath() + "?" + uri.getRawQuery();

        mockMvc.perform(get(relativePath))
                .andExpect(status().isOk());
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Test 6: Inactive and ineligible workers are filtered from public search
    // ───────────────────────────────────────────────────────────────────────────
    @Test
    void inactiveAndIneligibleWorkersAreFilteredFromPublicSearch() throws Exception {
        // Inactive worker
        workerRepository.saveAndFlush(Worker.builder()
                .firstName("Inactive")
                .lastName("Worker")
                .phoneNumber("+41790000099")
                .active(false)
                .verified(true)
                .category(category)
                .primaryCity("Zurich")
                .build());

        // Unverified worker
        workerRepository.saveAndFlush(Worker.builder()
                .firstName("Unverified")
                .lastName("Worker")
                .phoneNumber("+41790000098")
                .active(true)
                .verified(false)
                .category(category)
                .primaryCity("Zurich")
                .build());

        // Ineligible worker with suspended/inactive user account
        UserAccount suspendedAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("suspended@test.com")
                .phoneNumber("+41790000097")
                .passwordHash("hash")
                .role(UserRole.WORKER)
                .active(false)
                .build());

        workerRepository.saveAndFlush(Worker.builder()
                .userAccount(suspendedAccount)
                .firstName("Suspended")
                .lastName("Worker")
                .phoneNumber("+41790000097")
                .active(true)
                .verified(true)
                .category(category)
                .primaryCity("Zurich")
                .build());

        // Public search should only return the active, verified worker (Hans Müller)
        mockMvc.perform(get("/api/v1/search/workers").param("city", "Zurich"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results", hasSize(1)))
                .andExpect(jsonPath("$.data.results[0].fullName").value("Hans Müller"));
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Test 7: Customer photo workflow upload & assignment authorization
    // ───────────────────────────────────────────────────────────────────────────
    @Test
    void customerPhotoWorkflowRequiresUploadAndAssignmentAuthorization() throws Exception {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01};
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", jpegBytes);

        // 1. Customer can upload PROFILE_PHOTO for CUSTOMER entity
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(file)
                        .param("fileCategory", "PROFILE_PHOTO")
                        .param("entityType", "CUSTOMER")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(customer1Auth)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileCategory").value("PROFILE_PHOTO"))
                .andReturn();

        String fileIdStr = JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.data.id");
        UUID fileId = UUID.fromString(fileIdStr);

        // 2. Customer can assign their uploaded photo
        mockMvc.perform(put("/api/v1/customers/me/profile-photo")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(customer1Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": "%s"
                                }
                                """.formatted(fileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profilePhotoFileId").value(fileId.toString()));

        // 3. Customer CANNOT upload CERTIFICATE
        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(file)
                        .param("fileCategory", "CERTIFICATE")
                        .param("entityId", UUID.randomUUID().toString())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(customer1Auth)))
                .andExpect(status().isForbidden());

        // 4. Assigning non-existent file fails with 404
        mockMvc.perform(put("/api/v1/customers/me/profile-photo")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(customer1Auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId": "%s"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }
}
