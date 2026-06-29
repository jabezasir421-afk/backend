package com.bluecollar.storage.service;

import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.storage.config.StorageProperties;
import com.bluecollar.storage.dto.StoredFileResponse;
import com.bluecollar.storage.entity.EntityType;
import com.bluecollar.storage.entity.FileCategory;
import com.bluecollar.storage.entity.StoredFile;
import com.bluecollar.storage.exception.FileNotFoundException;
import com.bluecollar.storage.exception.FileUploadException;
import com.bluecollar.storage.mapper.StoredFileMapper;
import com.bluecollar.storage.repository.StoredFileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoredFileServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private StoredFileMapper storedFileMapper;

    @Mock
    private StorageProperties storageProperties;

    @InjectMocks
    private StoredFileServiceImpl storedFileService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private UUID userId;
    private UUID fileId;
    private AuthenticatedUser workerUser;
    private byte[] jpegBytes;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        fileId = UUID.randomUUID();
        workerUser = new AuthenticatedUser(userId, "worker@example.com", UserRole.WORKER);
        jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01};
        securityUtilsMock = Mockito.mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(workerUser);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    void uploadShouldThrowWhenFileIsEmpty() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThrows(FileUploadException.class, () ->
                storedFileService.upload(file, FileCategory.PROFILE_PHOTO, EntityType.WORKER, null));

        verify(storedFileRepository, never()).save(any());
    }

    @Test
    void uploadShouldThrowWhenContentTypeIsMissing() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenReturn(jpegBytes);
        when(file.getContentType()).thenReturn(null);

        assertThrows(FileUploadException.class, () ->
                storedFileService.upload(file, FileCategory.PROFILE_PHOTO, EntityType.WORKER, null));

        verify(storedFileRepository, never()).save(any());
    }

    @Test
    void uploadShouldThrowWhenMimeTypeIsNotAllowed() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenReturn(jpegBytes);
        when(file.getContentType()).thenReturn("application/pdf");

        assertThrows(FileUploadException.class, () ->
                storedFileService.upload(file, FileCategory.PROFILE_PHOTO, EntityType.WORKER, null));

        verify(storedFileRepository, never()).save(any());
    }

    @Test
    void uploadShouldThrowWhenContentDoesNotMatchDeclaredMimeType() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x00, 0x00, 0x00, 0x00};
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenReturn(pngHeader);
        when(file.getContentType()).thenReturn("image/jpeg");

        assertThrows(FileUploadException.class, () ->
                storedFileService.upload(file, FileCategory.PROFILE_PHOTO, EntityType.WORKER, null));

        verify(storedFileRepository, never()).save(any());
    }

    @Test
    void uploadShouldStoreFileAndReturnResponseWhenValid() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenReturn(jpegBytes);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getOriginalFilename()).thenReturn("photo.jpg");
        when(storageProperties.getPresignedUrlExpirySeconds()).thenReturn(3600L);
        when(fileStorageService.getDownloadUrl(anyString(), eq(3600L))).thenReturn("http://download/photo.jpg");

        StoredFile savedFile = StoredFile.builder()
                .ownerUserId(userId)
                .entityType(EntityType.WORKER)
                .fileCategory(FileCategory.PROFILE_PHOTO)
                .storageKey("key")
                .originalName("photo.jpg")
                .mimeType("image/jpeg")
                .sizeBytes((long) jpegBytes.length)
                .checksumSha256("abc123")
                .active(true)
                .build();
        savedFile.setId(fileId);
        savedFile.setCreatedAt(Instant.now());

        StoredFileResponse response = new StoredFileResponse(
                fileId,
                FileCategory.PROFILE_PHOTO.name(),
                "photo.jpg",
                "image/jpeg",
                jpegBytes.length,
                "http://download/photo.jpg",
                savedFile.getCreatedAt()
        );

        when(storedFileRepository.save(any(StoredFile.class))).thenReturn(savedFile);
        when(storedFileMapper.toResponse(savedFile, "http://download/photo.jpg")).thenReturn(response);

        StoredFileResponse result = storedFileService.upload(
                file,
                FileCategory.PROFILE_PHOTO,
                EntityType.WORKER,
                null
        );

        assertEquals(response, result);
        verify(fileStorageService).store(anyString(), any(), eq("image/jpeg"), eq((long) jpegBytes.length));
        verify(storedFileRepository).save(any(StoredFile.class));
    }

    @Test
    void getByIdShouldReturnFileWhenItExists() {
        StoredFile storedFile = buildActiveFile();
        StoredFileResponse response = new StoredFileResponse(
                fileId,
                FileCategory.PROFILE_PHOTO.name(),
                "photo.jpg",
                "image/jpeg",
                jpegBytes.length,
                "http://download/photo.jpg",
                Instant.now()
        );

        when(storedFileRepository.findByIdAndActiveTrue(fileId)).thenReturn(Optional.of(storedFile));
        when(storageProperties.getPresignedUrlExpirySeconds()).thenReturn(3600L);
        when(fileStorageService.getDownloadUrl(storedFile.getStorageKey(), 3600L))
                .thenReturn("http://download/photo.jpg");
        when(storedFileMapper.toResponse(storedFile, "http://download/photo.jpg")).thenReturn(response);

        StoredFileResponse result = storedFileService.getById(fileId);

        assertEquals(response, result);
        verify(storedFileRepository).findByIdAndActiveTrue(fileId);
    }

    @Test
    void getByIdShouldThrowFileNotFoundExceptionWhenFileDoesNotExist() {
        when(storedFileRepository.findByIdAndActiveTrue(fileId)).thenReturn(Optional.empty());

        assertThrows(FileNotFoundException.class, () -> storedFileService.getById(fileId));
        verify(storedFileRepository).findByIdAndActiveTrue(fileId);
    }

    @Test
    void deleteShouldSoftDeleteActiveFile() {
        StoredFile storedFile = buildActiveFile();
        when(storedFileRepository.findByIdAndActiveTrue(fileId)).thenReturn(Optional.of(storedFile));
        when(storedFileRepository.save(storedFile)).thenReturn(storedFile);

        storedFileService.delete(fileId);

        ArgumentCaptor<StoredFile> captor = ArgumentCaptor.forClass(StoredFile.class);
        verify(storedFileRepository).save(captor.capture());
        assertEquals(false, captor.getValue().getActive());
    }

    @Test
    void deleteShouldThrowFileNotFoundExceptionWhenFileDoesNotExist() {
        when(storedFileRepository.findByIdAndActiveTrue(fileId)).thenReturn(Optional.empty());

        assertThrows(FileNotFoundException.class, () -> storedFileService.delete(fileId));
        verify(storedFileRepository, never()).save(any());
    }

    private StoredFile buildActiveFile() {
        StoredFile storedFile = StoredFile.builder()
                .ownerUserId(userId)
                .entityType(EntityType.WORKER)
                .fileCategory(FileCategory.PROFILE_PHOTO)
                .storageKey("owner/profile/uuid/photo.jpg")
                .originalName("photo.jpg")
                .mimeType("image/jpeg")
                .sizeBytes((long) jpegBytes.length)
                .active(true)
                .build();
        storedFile.setId(fileId);
        return storedFile;
    }
}
