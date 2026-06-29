package com.bluecollar.storage.dto;

import java.time.Instant;
import java.util.UUID;

public record StoredFileResponse(
        UUID id,
        String fileCategory,
        String originalName,
        String mimeType,
        long sizeBytes,
        String downloadUrl,
        Instant createdAt
) {
}
