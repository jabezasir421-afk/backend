package com.bluecollar.storage.service;

import com.bluecollar.storage.dto.StoredFileResponse;
import com.bluecollar.storage.entity.EntityType;
import com.bluecollar.storage.entity.FileCategory;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface StoredFileService {

    StoredFileResponse upload(
            MultipartFile file,
            FileCategory fileCategory,
            EntityType entityType,
            UUID entityId
    );

    StoredFileResponse getById(UUID id);

    void delete(UUID id);

    String getPublicDownloadUrl(UUID fileId);
}
