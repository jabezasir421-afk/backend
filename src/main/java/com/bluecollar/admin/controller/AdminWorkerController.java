package com.bluecollar.admin.controller;

import com.bluecollar.admin.service.AdminWorkerService;
import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.worker.dto.WorkerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
            description = "Admin endpoint requiring JWT token with ADMIN role. Mark a worker as verified.",
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
}
