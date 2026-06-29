package com.bluecollar.storage.service;

import java.io.InputStream;

public interface FileStorageService {

    void store(String key, InputStream inputStream, String contentType, long sizeBytes);

    void delete(String key);

    String getDownloadUrl(String key, long expirySeconds);

    boolean exists(String key);
}
