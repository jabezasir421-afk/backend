package com.bluecollar.storage.controller;

import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.storage.dto.FileDownloadInfo;
import com.bluecollar.storage.dto.StoredFileResponse;
import com.bluecollar.storage.exception.FileUploadException;
import com.bluecollar.storage.entity.EntityType;
import com.bluecollar.storage.entity.FileCategory;
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
@PreAuthorize("hasAnyRole('CUSTOMER', 'WORKER', 'ADMIN')")
public class FileController {

    private final StoredFileService storedFileService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a file",
            description = "Upload a file with validation. File category determines allowed entity types:\n" +
                    "- PROFILE_PHOTO: entityType=WORKER (required), entityId=worker-id (required)\n" +
                    "- PORTFOLIO_IMAGE: entityType=PORTFOLIO (required), entityId=portfolio-id (required)\n" +
                    "- CERTIFICATE: entityType=CERTIFICATE (required), entityId=certificate-id (required)\n" +
                    "- IDENTITY_DOC: entityType=IDENTITY_DOC (required), entityId=identity-doc-id (required)",
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
    @Operation(summary = "Get file details", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<StoredFileResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(storedFileService.getById(id), "File fetched successfully"));
    }

    @GetMapping("/{id}/download")
    @Operation(
            summary = "Download a file",
            description = "Stream file content for download. IDENTITY_DOC files can only be downloaded by admins.",
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

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a file (soft-delete)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        storedFileService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "File deleted successfully"));
    }
}
