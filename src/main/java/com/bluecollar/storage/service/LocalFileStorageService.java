package com.bluecollar.storage.service;

import com.bluecollar.storage.config.StorageProperties;
import com.bluecollar.storage.exception.FileUploadException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

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

    private final byte[] signingKey = "bluecollar-secure-storage-signed-url-secret-key-32bytes".getBytes(StandardCharsets.UTF_8);

    @Override
    public String getDownloadUrl(String key, long expirySeconds) {
        long expires = Instant.now().getEpochSecond() + expirySeconds;
        String signature = generateSignature(key, expires);
        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
        return resolveBaseUrl() + "/api/v1/files/signed?key=" + encodedKey + "&expires=" + expires + "&signature=" + signature;
    }

    @Override
    public boolean validateSignedUrl(String key, long expires, String signature) {
        if (key == null || signature == null) {
            return false;
        }
        if (Instant.now().getEpochSecond() > expires) {
            return false;
        }
        String expected = generateSignature(key, expires);
        if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        try {
            String decoded = java.net.URLDecoder.decode(key, StandardCharsets.UTF_8);
            String expectedDecoded = generateSignature(decoded, expires);
            return MessageDigest.isEqual(expectedDecoded.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String generateSignature(String key, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            byte[] rawHmac = mac.doFinal((key + ":" + expires).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to generate signature for signed URL", ex);
        }
    }

    private String resolveBaseUrl() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String scheme = request.getScheme();
                String serverName = request.getServerName();
                int serverPort = request.getServerPort();
                if ((scheme.equals("http") && serverPort == 80) || (scheme.equals("https") && serverPort == 443)) {
                    return scheme + "://" + serverName;
                }
                return scheme + "://" + serverName + ":" + serverPort;
            }
        } catch (Exception ignored) {
        }
        return "http://localhost:8080";
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolvePath(key));
    }

    @Override
    public InputStream getInputStream(String key) {
        try {
            return Files.newInputStream(resolvePath(key));
        } catch (IOException ex) {
            throw new FileUploadException(
                    "Failed to read file: " + key,
                    ex
            );
        }
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