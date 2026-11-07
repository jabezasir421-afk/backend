package com.bluecollar.admin.controller;

import com.bluecollar.admin.dto.AdminIdentityDocumentResponse;
import com.bluecollar.admin.dto.RejectDocumentRequest;
import com.bluecollar.admin.service.AdminWorkerService;
import com.bluecollar.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/documents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Documents", description = "Admin identity document review and verification")
public class AdminDocumentController {

    private final AdminWorkerService adminWorkerService;

    @GetMapping("/pending")
    @Operation(
            summary = "List pending identity documents",
            description = "Admin endpoint requiring JWT token with ADMIN role. Retrieve a paginated list of worker identity documents pending review.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Page<AdminIdentityDocumentResponse>>> listPendingDocuments(
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminWorkerService.listPendingIdentityDocuments(pageable), "Pending identity documents fetched successfully"));
    }

    @PutMapping("/{documentId}/approve")
    @Operation(
            summary = "Approve identity document",
            description = "Admin endpoint requiring JWT token with ADMIN role. Approve a pending worker identity document.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<AdminIdentityDocumentResponse>> approveDocument(@PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.success(adminWorkerService.approveIdentityDocument(documentId), "Identity document approved successfully"));
    }

    @PutMapping("/{documentId}/reject")
    @Operation(
            summary = "Reject identity document",
            description = "Admin endpoint requiring JWT token with ADMIN role. Reject a pending worker identity document with a reason.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<AdminIdentityDocumentResponse>> rejectDocument(
            @PathVariable UUID documentId,
            @Valid @RequestBody RejectDocumentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminWorkerService.rejectIdentityDocument(documentId, request), "Identity document rejected successfully"));
    }
}
