package com.bluecollar.storage.service;

import com.bluecollar.storage.config.StorageProperties;
import com.bluecollar.storage.exception.FileUploadException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "bluecollar.storage.type",
        havingValue = "local",
        matchIfMissing = true
)
public class LocalFileStorageService implements FileStorageService {

    private final StorageProperties storageProperties;

    @Override
    public void store(String key,
                      InputStream inputStream,
                      String contentType,
                      long sizeBytes) {

        Path targetPath = resolvePath(key);
        Path parent = targetPath.getParent();

        if (parent == null) {
            throw new FileUploadException(
                    "Unable to determine parent directory for: " + targetPath);
        }

        try {
            Files.createDirectories(parent);

            Files.copy(
                    inputStream,
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING
            );

        } catch (IOException ex) {
            throw new FileUploadException(
                    "Failed to store file: " + key,
                    ex
            );
        }
    }

    @Override
    public void delete(String key) {

        Path targetPath = resolvePath(key);

        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException ex) {
            throw new FileUploadException(
                    "Failed to delete file: " + key,
                    ex
            );
        }
    }

    @Override
    public String getDownloadUrl(String key, long expirySeconds) {
        return resolvePath(key).toUri().toString();
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolvePath(key));
    }

    private Path resolvePath(String key) {

        Path baseDirectory = Path.of(storageProperties.getLocal().getBasePath())
                .toAbsolutePath()
                .normalize();

        Path resolvedPath = baseDirectory
                .resolve(key)
                .normalize();

        // Prevent ../../ path traversal attacks
        if (!resolvedPath.startsWith(baseDirectory)) {
            throw new FileUploadException("Invalid storage key: " + key);
        }

        return resolvedPath;
    }
}