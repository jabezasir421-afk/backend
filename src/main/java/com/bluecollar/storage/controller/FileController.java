package com.bluecollar.storage.controller;

import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.common.exception.UnauthorizedException;
import com.bluecollar.storage.dto.FileDownloadInfo;
import com.bluecollar.storage.dto.StoredFileResponse;
import com.bluecollar.storage.entity.EntityType;
import com.bluecollar.storage.entity.FileCategory;
import com.bluecollar.storage.exception.FileNotFoundException;
import com.bluecollar.storage.service.FileStorageService;
import com.bluecollar.storage.service.StoredFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "File Storage", description = "File upload and management")
public class FileController {

    private final StoredFileService storedFileService;
    private final FileStorageService fileStorageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CUSTOMER', 'WORKER', 'ADMIN')")
    @Operation(
            summary = "Upload a file",
            description = "Upload a file with validation. File is owned by authenticated user. " +
                    "File category determines allowed entity types:\n" +
                    "- PROFILE_PHOTO: entityType=WORKER or CUSTOMER\n" +
                    "- PORTFOLIO_IMAGE: entityType=PORTFOLIO\n" +
                    "- CERTIFICATE: entityType=CERTIFICATE\n" +
                    "- IDENTITY_DOC: entityType=IDENTITY_DOC",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<StoredFileResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileCategory") FileCategory fileCategory,
            @RequestParam(value = "entityType", required = false) EntityType entityType,
            @RequestParam(value = "entityId", required = false) UUID entityId
    ) {
        StoredFileResponse response = storedFileService.upload(file, fileCategory, entityType, entityId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "File uploaded successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'WORKER', 'ADMIN')")
    @Operation(
            summary = "Get file details",
            description = "Retrieve file metadata including download URL (if applicable)",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<StoredFileResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(storedFileService.getById(id), "File fetched successfully"));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'WORKER', 'ADMIN')")
    @Operation(
            summary = "Download a file",
            description = "Stream file content for download. Authenticated endpoint ensures files are accessed securely. " +
                    "CRITICAL SECURITY: IDENTITY_DOC can ONLY be downloaded by ADMIN role. " +
                    "CERTIFICATE files can ONLY be downloaded by ADMIN or owning worker.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<?> download(@PathVariable UUID id) {
        FileDownloadInfo info = storedFileService.download(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(info.mimeType()));
        headers.setContentLength(info.sizeBytes());
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(info.filename(), StandardCharsets.UTF_8)
                        .build()
        );

        return ResponseEntity.ok()
                .headers(headers)
                .body(new org.springframework.core.io.InputStreamResource(info.inputStream()));
    }

    @GetMapping("/signed")
    @Operation(
            summary = "Download public or signed file",
            description = "Download file using short-lived signed URL without requiring bearer token header",
            security = {}
    )
    public ResponseEntity<?> downloadSigned(
            @RequestParam("key") String key,
            @RequestParam("expires") long expires,
            @RequestParam("signature") String signature
    ) {
        String resolvedKey = key;
        try {
            if (key != null && key.contains("%")) {
                resolvedKey = java.net.URLDecoder.decode(key, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }

        if (!fileStorageService.validateSignedUrl(resolvedKey, expires, signature)) {
            throw new UnauthorizedException("Invalid or expired download link");
        }

        if (!fileStorageService.exists(resolvedKey)) {
            throw new FileNotFoundException("File not found for key: " + resolvedKey);
        }

        var inputStream = fileStorageService.getInputStream(resolvedKey);
        String filename = extractFilename(resolvedKey);
        String mimeType = probeContentType(resolvedKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mimeType));
        headers.setContentDisposition(
                ContentDisposition.inline()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
        );

        return ResponseEntity.ok()
                .headers(headers)
                .body(new org.springframework.core.io.InputStreamResource(inputStream));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'WORKER', 'ADMIN')")
    @Operation(summary = "Delete a file (soft-delete)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        storedFileService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "File deleted successfully"));
    }

    private String extractFilename(String key) {
        int lastSlash = key.lastIndexOf('/');
        return lastSlash >= 0 ? key.substring(lastSlash + 1) : key;
    }

    private String probeContentType(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
