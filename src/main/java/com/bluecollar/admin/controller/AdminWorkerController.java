package com.bluecollar.admin.controller;

import com.bluecollar.admin.service.AdminWorkerService;
import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.worker.dto.WorkerResponse;
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
public class AdminWorkerController {

    private final AdminWorkerService adminWorkerService;

    @PutMapping("/{id}/verify")
    public ResponseEntity<ApiResponse<WorkerResponse>> verifyWorker(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminWorkerService.verifyWorker(id), "Worker verified successfully"));
    }

    @PutMapping("/{id}/unverify")
    public ResponseEntity<ApiResponse<WorkerResponse>> unverifyWorker(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminWorkerService.unverifyWorker(id), "Worker unverified successfully"));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<WorkerResponse>> deactivateWorker(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminWorkerService.deactivateWorker(id), "Worker deactivated successfully"));
    }
}
