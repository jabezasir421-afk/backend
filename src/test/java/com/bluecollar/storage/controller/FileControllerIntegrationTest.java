package com.bluecollar.storage.controller;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.storage.entity.FileCategory;
import com.bluecollar.storage.entity.StoredFile;
import com.bluecollar.storage.repository.StoredFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FileControllerIntegrationTest {

    private static final byte[] JPEG_BYTES = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
    };

    @DynamicPropertySource
    static void configureStoragePath(DynamicPropertyRegistry registry) {
        registry.add("bluecollar.storage.local.base-path", () ->
                Path.of(System.getProperty("java.io.tmpdir"), "bluecollar-file-tests").toString());
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    private UserAccount savedUserAccount;
    private UsernamePasswordAuthenticationToken workerAuth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        storedFileRepository.deleteAll();
        userAccountRepository.deleteAll();

        savedUserAccount = userAccountRepository.saveAndFlush(buildUserAccount(
                "worker.upload@example.com",
                "+919876543299",
                UserRole.WORKER
        ));

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                savedUserAccount.getId(),
                savedUserAccount.getEmail(),
                UserRole.WORKER
        );
        workerAuth = new UsernamePasswordAuthenticationToken(
                authenticatedUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_WORKER"))
        );
    }

    @Test
    void uploadShouldStoreJpegFileAndReturnApiResponse() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "profile.jpg",
                "image/jpeg",
                JPEG_BYTES
        );

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(file)
                        .param("fileCategory", FileCategory.PROFILE_PHOTO.name())
                        .with(authentication(workerAuth)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("File uploaded successfully"))
                .andExpect(jsonPath("$.data.fileCategory").value("PROFILE_PHOTO"))
                .andExpect(jsonPath("$.data.originalName").value("profile.jpg"))
                .andExpect(jsonPath("$.data.mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.data.sizeBytes").value(JPEG_BYTES.length));
    }

    @Test
    void getByIdShouldReturnUploadedFile() throws Exception {
        StoredFile storedFile = storedFileRepository.saveAndFlush(buildStoredFile(savedUserAccount.getId()));

        mockMvc.perform(get("/api/v1/files/{id}", storedFile.getId())
                        .with(authentication(workerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("File fetched successfully"))
                .andExpect(jsonPath("$.data.id").value(storedFile.getId().toString()))
                .andExpect(jsonPath("$.data.originalName").value("profile.jpg"));
    }

    @Test
    void getByIdShouldReturnNotFoundForMissingFile() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/files/{id}", missingId)
                        .with(authentication(workerAuth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("File not found with id: " + missingId));
    }

    @Test
    void deleteShouldSoftDeleteExistingFile() throws Exception {
        StoredFile storedFile = storedFileRepository.saveAndFlush(buildStoredFile(savedUserAccount.getId()));

        mockMvc.perform(delete("/api/v1/files/{id}", storedFile.getId())
                        .with(authentication(workerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("File deleted successfully"));

        assertFalse(storedFileRepository.findByIdAndActiveTrue(storedFile.getId()).isPresent());
    }

    @Test
    void uploadShouldReturnBadRequestForInvalidMimeType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "%PDF-1.4".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(file)
                        .param("fileCategory", FileCategory.PROFILE_PHOTO.name())
                        .with(authentication(workerAuth)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "MIME type 'application/pdf' is not allowed for category PROFILE_PHOTO"));
    }

    private UserAccount buildUserAccount(String email, String phone, UserRole role) {
        return UserAccount.builder()
                .email(email)
                .phoneNumber(phone)
                .passwordHash("hashed_password")
                .role(role)
                .active(true)
                .emailVerified(false)
                .phoneVerified(false)
                .build();
    }

    private StoredFile buildStoredFile(UUID ownerUserId) {
        return StoredFile.builder()
                .ownerUserId(ownerUserId)
                .entityType(com.bluecollar.storage.entity.EntityType.WORKER)
                .fileCategory(FileCategory.PROFILE_PHOTO)
                .storageKey(ownerUserId + "/profile_photo/" + UUID.randomUUID() + "/profile.jpg")
                .originalName("profile.jpg")
                .mimeType("image/jpeg")
                .sizeBytes((long) JPEG_BYTES.length)
                .active(true)
                .build();
    }
}
