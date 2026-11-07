package com.bluecollar.admin.controller;

import com.bluecollar.admin.dto.AdminIdentityDocumentResponse;
import com.bluecollar.admin.dto.LinkWorkerUserRequest;
import com.bluecollar.admin.dto.RejectDocumentRequest;
import com.bluecollar.admin.service.AdminWorkerService;
import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.worker.dto.WorkerResponse;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/workers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Workers", description = "Admin worker verification and status control")
public class AdminWorkerController {

    private final AdminWorkerService adminWorkerService;

    @PutMapping("/{id}/verify")
    @Operation(
            summary = "Verify worker",
            description = "Admin endpoint requiring JWT token with ADMIN role. Mark a worker as verified after all identity documents are approved.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<WorkerResponse>> verifyWorker(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminWorkerService.verifyWorker(id), "Worker verified successfully"));
    }

    @PutMapping("/{id}/unverify")
    @Operation(
            summary = "Unverify worker",
            description = "Admin endpoint requiring JWT token with ADMIN role. Mark a worker as unverified.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<WorkerResponse>> unverifyWorker(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminWorkerService.unverifyWorker(id), "Worker unverified successfully"));
    }

    @PutMapping("/{id}/deactivate")
    @Operation(
            summary = "Deactivate worker",
            description = "Admin endpoint requiring JWT token with ADMIN role. Deactivate a worker account.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<WorkerResponse>> deactivateWorker(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminWorkerService.deactivateWorker(id), "Worker deactivated successfully"));
    }

    @PutMapping("/{id}/link-user")
    @Operation(
            summary = "Link worker to user account",
            description = "Admin endpoint requiring JWT token with ADMIN role. Link an admin-created worker profile to an existing user account with matching email/phone.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<WorkerResponse>> linkWorkerUser(
            @PathVariable UUID id,
            @Valid @RequestBody LinkWorkerUserRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminWorkerService.linkUserAccount(id, request), "Worker linked to user account successfully"));
    }

    @GetMapping("/documents/pending")
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

    @GetMapping("/{id}/documents")
    @Operation(
            summary = "List documents for worker",
            description = "Admin endpoint requiring JWT token with ADMIN role. Retrieve all identity documents for a specific worker.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<List<AdminIdentityDocumentResponse>>> getWorkerDocuments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminWorkerService.getWorkerIdentityDocuments(id), "Worker identity documents fetched successfully"));
    }

    @PutMapping("/documents/{documentId}/approve")
    @Operation(
            summary = "Approve identity document",
            description = "Admin endpoint requiring JWT token with ADMIN role. Approve a pending worker identity document.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<AdminIdentityDocumentResponse>> approveDocument(@PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.success(adminWorkerService.approveIdentityDocument(documentId), "Identity document approved successfully"));
    }

    @PutMapping("/documents/{documentId}/reject")
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
