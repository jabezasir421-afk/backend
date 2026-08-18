package com.bluecollar.storage.dto;

import java.io.InputStream;

public record FileDownloadInfo(
        InputStream inputStream,
        String mimeType,
        String filename,
        long sizeBytes
) {
}
