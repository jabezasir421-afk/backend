package com.bluecollar.storage.service;

import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.common.exception.UnauthorizedException;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
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

    @Override
    public StoredFileResponse upload(
            MultipartFile file,
            FileCategory fileCategory,
            EntityType entityType,
            UUID entityId
    ) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new FileUploadException("Failed to read uploaded file", exception);
        }

        validateUpload(file, fileCategory, content);
        validateFileCategory(fileCategory, entityType, entityId);

        EntityType resolvedEntityType = entityType == null ? mapDefaultEntityType(fileCategory) : entityType;
        validateCategoryEntityMatch(fileCategory, resolvedEntityType);

        String storageKey = buildStorageKey(currentUser.userAccountId(), fileCategory, file.getOriginalFilename());
        String checksum = storeWithChecksum(storageKey, content, file.getContentType());

        StoredFile storedFile = StoredFile.builder()
                .ownerUserId(currentUser.userAccountId())
                .entityType(resolvedEntityType)
                .entityId(entityId)
                .fileCategory(fileCategory)
                .storageKey(storageKey)
                .originalName(sanitizeFilename(file.getOriginalFilename()))
                .mimeType(file.getContentType())
                .sizeBytes((long) content.length)
                .checksumSha256(checksum)
                .build();

        StoredFile savedFile = storedFileRepository.save(storedFile);
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

        switch (storedFile.getFileCategory()) {
            case IDENTITY_DOC:
            case CERTIFICATE:
                throw new UnauthorizedException("You do not have access to this file");
            case PROFILE_PHOTO:
            case PORTFOLIO_IMAGE:
                if (!storedFile.getOwnerUserId().equals(currentUser.userAccountId())) {
                    throw new UnauthorizedException("You do not have access to this file");
                }
                break;
        }
    }

    private void validateFileDownloadAuthorization(StoredFile storedFile, AuthenticatedUser currentUser) {
        if (currentUser.role() == UserRole.ADMIN) {
            return;
        }

        if (storedFile.getFileCategory() == FileCategory.IDENTITY_DOC) {
            throw new UnauthorizedException("You do not have access to this file");
        }

        if (storedFile.getFileCategory() == FileCategory.CERTIFICATE) {
            throw new UnauthorizedException("You do not have access to this file");
        }

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

    private void validateUpload(MultipartFile file, FileCategory fileCategory, byte[] content) {
        if (file == null || file.isEmpty()) {
            throw new FileUploadException("File is required");
        }

        String mimeType = file.getContentType();
        if (mimeType == null || mimeType.isBlank()) {
            throw new FileUploadException("Content type is required");
        }

        long maxSize = isImageCategory(fileCategory) ? IMAGE_MAX_BYTES : DOCUMENT_MAX_BYTES;
        if (content.length > maxSize) {
            throw new FileUploadException("File exceeds maximum allowed size for category " + fileCategory);
        }

        Set<String> allowedMimeTypes = isImageCategory(fileCategory) ? IMAGE_MIME_TYPES : DOCUMENT_MIME_TYPES;
        if (!allowedMimeTypes.contains(mimeType)) {
            throw new FileUploadException("MIME type '" + mimeType + "' is not allowed for category " + fileCategory);
        }

        int headerLength = Math.min(content.length, 12);
        byte[] header = new byte[headerLength];
        System.arraycopy(content, 0, header, 0, headerLength);
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

        // Note: entityId ownership validation should happen at resource-level endpoints
        // (e.g., POST /workers/me/portfolio/certificates validates certificate ownership)
        // The upload endpoint stores with ownerUserId, so file access is always validated via ownership.
    }

    private void validateCategoryEntityMatch(FileCategory fileCategory, EntityType entityType) {
        boolean valid = switch (fileCategory) {
            case PROFILE_PHOTO -> entityType == EntityType.WORKER;
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

    private String storeWithChecksum(String storageKey, byte[] content, String contentType) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = new java.io.ByteArrayInputStream(content);
                 DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                fileStorageService.store(storageKey, digestInputStream, contentType, content.length);
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

    private EntityType mapDefaultEntityType(FileCategory fileCategory) {
        return switch (fileCategory) {
            case PROFILE_PHOTO -> EntityType.WORKER;
            case PORTFOLIO_IMAGE -> EntityType.PORTFOLIO;
            case CERTIFICATE -> EntityType.CERTIFICATE;
            case IDENTITY_DOC -> EntityType.IDENTITY_DOC;
        };
    }
}
