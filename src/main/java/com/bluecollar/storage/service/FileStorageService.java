package com.bluecollar.storage.service;

import java.io.InputStream;

public interface FileStorageService {

    void store(String key, InputStream inputStream, String contentType, long sizeBytes);

    void delete(String key);

    String getDownloadUrl(String key, long expirySeconds);

    boolean exists(String key);

    InputStream getInputStream(String key);

    default boolean validateSignedUrl(String key, long expires, String signature) {
        return true;
    }
}
