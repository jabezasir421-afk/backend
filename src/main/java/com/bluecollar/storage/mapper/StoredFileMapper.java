package com.bluecollar.storage.mapper;

import com.bluecollar.storage.dto.StoredFileResponse;
import com.bluecollar.storage.entity.StoredFile;
import org.springframework.stereotype.Component;

@Component
public class StoredFileMapper {

    public StoredFileResponse toResponse(StoredFile storedFile, String downloadUrl) {
        return new StoredFileResponse(
                storedFile.getId(),
                storedFile.getFileCategory().name(),
                storedFile.getOriginalName(),
                storedFile.getMimeType(),
                storedFile.getSizeBytes(),
                downloadUrl,
                storedFile.getCreatedAt()
        );
    }
}
