package com.bluecollar.storage.service;

import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.common.exception.UnauthorizedException;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.storage.config.StorageProperties;
import com.bluecollar.storage.dto.FileDownloadInfo;
import com.bluecollar.storage.dto.StoredFileResponse;
import com.bluecollar.storage.entity.EntityType;
import com.bluecollar.storage.entity.FileCategory;
import com.bluecollar.storage.entity.StoredFile;
import com.bluecollar.storage.exception.FileNotFoundException;
import com.bluecollar.storage.exception.FileUploadException;
import com.bluecollar.storage.mapper.StoredFileMapper;
import com.bluecollar.storage.repository.StoredFileRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class StoredFileServiceImpl implements StoredFileService {

    private static final long IMAGE_MAX_BYTES = 5L * 1024 * 1024;
    private static final long DOCUMENT_MAX_BYTES = 10L * 1024 * 1024;

    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private static final Set<String> DOCUMENT_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/pdf"
    );

    private final StoredFileRepository storedFileRepository;
    private final FileStorageService fileStorageService;
    private final StoredFileMapper storedFileMapper;
    private final StorageProperties storageProperties;
    private final CustomerRepository customerRepository;
    private final WorkerRepository workerRepository;

    @Override
    public StoredFileResponse upload(
            MultipartFile file,
            FileCategory fileCategory,
            EntityType entityType,
            UUID entityId
    ) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();

        if (currentUser.role() == UserRole.CUSTOMER && fileCategory != FileCategory.PROFILE_PHOTO) {
            throw new UnauthorizedException("Customers can only upload profile photos");
        }

        if (file == null || file.isEmpty()) {
            throw new FileUploadException("File is required");
        }

        // Validate magic header without buffering entire file in memory
        byte[] header = new byte[12];
        InputStream rawStream;
        try {
            rawStream = file.getInputStream();
        } catch (IOException exception) {
            throw new FileUploadException("Failed to read uploaded file", exception);
        }

        BufferedInputStream bufferedStream = new BufferedInputStream(rawStream);
        bufferedStream.mark(16);
        int read;
        try {
            read = bufferedStream.read(header);
            bufferedStream.reset();
        } catch (IOException ex) {
            throw new FileUploadException("Failed to inspect file header", ex);
        }

        byte[] actualHeader = read > 0 && read < 12 ? Arrays.copyOf(header, read) : header;
        validateUpload(file, fileCategory, actualHeader);
        validateFileCategory(fileCategory, entityType, entityId);

        EntityType resolvedEntityType = entityType == null ? mapDefaultEntityType(fileCategory, currentUser) : entityType;
        validateCategoryEntityMatch(fileCategory, resolvedEntityType);

        if (resolvedEntityType == EntityType.CUSTOMER && entityId != null) {
            Customer customer = customerRepository.findByUserAccountId(currentUser.userAccountId())
                    .orElseThrow(() -> new UnauthorizedException("Customer profile not found"));
            if (!customer.getId().equals(entityId)) {
                throw new UnauthorizedException("Cannot upload file for another customer");
            }
        }

        if (resolvedEntityType == EntityType.WORKER && entityId != null && currentUser.role() == UserRole.WORKER) {
            Worker worker = workerRepository.findByUserAccountId(currentUser.userAccountId())
                    .orElseThrow(() -> new UnauthorizedException("Worker profile not found"));
            if (!worker.getId().equals(entityId)) {
                throw new UnauthorizedException("Cannot upload file for another worker");
            }
        }

        String storageKey = buildStorageKey(currentUser.userAccountId(), fileCategory, file.getOriginalFilename());
        // Stream directly into storage calculating SHA-256 digest on the fly
        String checksum = storeWithChecksum(storageKey, bufferedStream, file.getContentType(), file.getSize());

        StoredFile storedFile = StoredFile.builder()
                .ownerUserId(currentUser.userAccountId())
                .entityType(resolvedEntityType)
                .entityId(entityId)
                .fileCategory(fileCategory)
                .storageKey(storageKey)
                .originalName(sanitizeFilename(file.getOriginalFilename()))
                .mimeType(file.getContentType())
                .sizeBytes(file.getSize())
                .checksumSha256(checksum)
                .build();

        StoredFile savedFile;
        try {
            savedFile = storedFileRepository.save(storedFile);
        } catch (Exception ex) {
            // Immediately delete uploaded file if database persistence fails
            try {
                fileStorageService.delete(storageKey);
            } catch (Exception deleteEx) {
                log.warn("Failed to delete orphaned file {} after persistence failure: {}", storageKey, deleteEx.getMessage());
            }
            throw ex;
        }

        // Register transaction rollback synchronization to clean up file if subsequent operations abort
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            final String cleanupKey = storageKey;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        try {
                            fileStorageService.delete(cleanupKey);
                            log.debug("Deleted uncommitted stored file {} after transaction rollback", cleanupKey);
                        } catch (Exception ex) {
                            log.warn("Failed to delete stored file {} on transaction rollback: {}", cleanupKey, ex.getMessage());
                        }
                    }
                }
            });
        }

        String downloadUrl = resolveDownloadUrl(savedFile, currentUser);
        return storedFileMapper.toResponse(savedFile, downloadUrl);
    }

    @Override
    @Transactional(readOnly = true)
    public StoredFileResponse getById(UUID id) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        StoredFile storedFile = findActiveFile(id);
        validateFileAuthorization(storedFile, currentUser);
        String downloadUrl = resolveDownloadUrl(storedFile, currentUser);
        return storedFileMapper.toResponse(storedFile, downloadUrl);
    }

    @Override
    @Transactional(readOnly = true)
    public String getPublicDownloadUrl(UUID fileId) {
        StoredFile storedFile = storedFileRepository.findByIdAndActiveTrue(fileId)
                .orElse(null);
        if (storedFile == null) {
            return null;
        }
        // SECURITY: Only PROFILE_PHOTO and PORTFOLIO_IMAGE are public
        // IDENTITY_DOC and CERTIFICATE URLs are never public - download only via authenticated endpoint
        if (storedFile.getFileCategory() != FileCategory.PROFILE_PHOTO
                && storedFile.getFileCategory() != FileCategory.PORTFOLIO_IMAGE) {
            return null;
        }
        // Use short-lived signed URLs (expires in seconds) rather than permanent public URLs
        return fileStorageService.getDownloadUrl(
                storedFile.getStorageKey(),
                storageProperties.getPresignedUrlExpirySeconds()
        );
    }

    @Override
    public void delete(UUID id) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        StoredFile storedFile = findActiveFile(id);
        validateFileAuthorization(storedFile, currentUser);
        storedFile.setActive(false);
        storedFileRepository.save(storedFile);
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadInfo download(UUID id) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        StoredFile storedFile = findActiveFile(id);
        validateFileDownloadAuthorization(storedFile, currentUser);

        var inputStream = fileStorageService.getInputStream(storedFile.getStorageKey());
        return new FileDownloadInfo(
                inputStream,
                storedFile.getMimeType(),
                storedFile.getOriginalName(),
                storedFile.getSizeBytes()
        );
    }

    private void validateFileAuthorization(StoredFile storedFile, AuthenticatedUser currentUser) {
        if (currentUser.role() == UserRole.ADMIN) {
            return;
        }

        if (!storedFile.getOwnerUserId().equals(currentUser.userAccountId())) {
            throw new UnauthorizedException("You do not have access to this file");
        }
    }

    private void validateFileDownloadAuthorization(StoredFile storedFile, AuthenticatedUser currentUser) {
        if (currentUser.role() == UserRole.ADMIN) {
            return;
        }

        // IDENTITY_DOC: admin-only
        if (storedFile.getFileCategory() == FileCategory.IDENTITY_DOC) {
            throw new UnauthorizedException("You do not have access to this file");
        }

        // CERTIFICATE, PROFILE_PHOTO, and PORTFOLIO_IMAGE: owner only
        if (!storedFile.getOwnerUserId().equals(currentUser.userAccountId())) {
            throw new UnauthorizedException("You do not have access to this file");
        }
    }

    private StoredFile findActiveFile(UUID id) {
        return storedFileRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new FileNotFoundException(id));
    }

    private String resolveDownloadUrl(StoredFile storedFile, AuthenticatedUser currentUser) {
        if (storedFile.getFileCategory() == FileCategory.IDENTITY_DOC && currentUser.role() != UserRole.ADMIN) {
            return null;
        }
        return fileStorageService.getDownloadUrl(
                storedFile.getStorageKey(),
                storageProperties.getPresignedUrlExpirySeconds()
        );
    }

    private void validateUpload(MultipartFile file, FileCategory fileCategory, byte[] header) {
        String mimeType = file.getContentType();
        if (mimeType == null || mimeType.isBlank()) {
            throw new FileUploadException("Content type is required");
        }

        long maxSize = isImageCategory(fileCategory) ? IMAGE_MAX_BYTES : DOCUMENT_MAX_BYTES;
        if (file.getSize() > maxSize) {
            throw new FileUploadException("File exceeds maximum allowed size for category " + fileCategory);
        }

        Set<String> allowedMimeTypes = isImageCategory(fileCategory) ? IMAGE_MIME_TYPES : DOCUMENT_MIME_TYPES;
        if (!allowedMimeTypes.contains(mimeType)) {
            throw new FileUploadException("MIME type '" + mimeType + "' is not allowed for category " + fileCategory);
        }

        if (!matchesMime(header, mimeType)) {
            throw new FileUploadException("File content does not match declared MIME type");
        }
    }

    private void validateFileCategory(FileCategory fileCategory, EntityType entityType, UUID entityId) {
        boolean requiresEntity = fileCategory == FileCategory.CERTIFICATE || fileCategory == FileCategory.IDENTITY_DOC;

        if (requiresEntity && entityId == null) {
            throw new FileUploadException("entityId is required for file category " + fileCategory);
        }

        if (!requiresEntity && entityId != null) {
            throw new FileUploadException("entityId should not be provided for file category " + fileCategory);
        }
    }

    private void validateCategoryEntityMatch(FileCategory fileCategory, EntityType entityType) {
        boolean valid = switch (fileCategory) {
            case PROFILE_PHOTO -> entityType == EntityType.WORKER || entityType == EntityType.CUSTOMER;
            case PORTFOLIO_IMAGE -> entityType == EntityType.PORTFOLIO;
            case CERTIFICATE -> entityType == EntityType.CERTIFICATE;
            case IDENTITY_DOC -> entityType == EntityType.IDENTITY_DOC;
        };

        if (!valid) {
            throw new FileUploadException("File category " + fileCategory + " cannot be used with entity type " + entityType);
        }
    }

    private boolean matchesMime(byte[] header, String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> header.length >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF;
            case "image/png" -> header.length >= 8
                    && header[0] == (byte) 0x89
                    && header[1] == 0x50
                    && header[2] == 0x4E
                    && header[3] == 0x47;
            case "image/webp" -> header.length >= 12
                    && header[0] == 0x52
                    && header[1] == 0x49
                    && header[2] == 0x46
                    && header[3] == 0x46
                    && header[8] == 0x57
                    && header[9] == 0x45
                    && header[10] == 0x42
                    && header[11] == 0x50;
            case "application/pdf" -> header.length >= 4
                    && header[0] == 0x25
                    && header[1] == 0x50
                    && header[2] == 0x44
                    && header[3] == 0x46;
            default -> false;
        };
    }

    private String storeWithChecksum(String storageKey, InputStream inputStream, String contentType, long size) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                fileStorageService.store(storageKey, digestInputStream, contentType, size);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new FileUploadException("Failed to store uploaded file", exception);
        }
    }

    private String buildStorageKey(UUID ownerUserId, FileCategory fileCategory, String originalFilename) {
        return ownerUserId + "/" + fileCategory.name().toLowerCase() + "/" + UUID.randomUUID() + "/"
                + sanitizeFilename(originalFilename);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private boolean isImageCategory(FileCategory fileCategory) {
        return fileCategory == FileCategory.PROFILE_PHOTO || fileCategory == FileCategory.PORTFOLIO_IMAGE;
    }

    private EntityType mapDefaultEntityType(FileCategory fileCategory, AuthenticatedUser currentUser) {
        return switch (fileCategory) {
            case PROFILE_PHOTO -> currentUser != null && currentUser.role() == UserRole.CUSTOMER
                    ? EntityType.CUSTOMER
                    : EntityType.WORKER;
            case PORTFOLIO_IMAGE -> EntityType.PORTFOLIO;
            case CERTIFICATE -> EntityType.CERTIFICATE;
            case IDENTITY_DOC -> EntityType.IDENTITY_DOC;
        };
    }
}
