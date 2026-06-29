package com.bluecollar.storage.controller;

import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.storage.dto.StoredFileResponse;
import com.bluecollar.storage.entity.EntityType;
import com.bluecollar.storage.entity.FileCategory;
import com.bluecollar.storage.service.StoredFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final StoredFileService storedFileService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
    public ResponseEntity<ApiResponse<StoredFileResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(storedFileService.getById(id), "File fetched successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        storedFileService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "File deleted successfully"));
    }
}
