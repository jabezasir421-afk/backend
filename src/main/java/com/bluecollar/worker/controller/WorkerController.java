package com.bluecollar.worker.controller;

import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.worker.dto.CreateWorkerRequest;
import com.bluecollar.worker.dto.UpdateWorkerRequest;
import com.bluecollar.worker.dto.WorkerResponse;
import com.bluecollar.worker.service.WorkerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workers")
@RequiredArgsConstructor
@Tag(name = "Workers", description = "Worker profile management and discovery")
public class WorkerController {

    private final WorkerService workerService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Provision a new worker (admin only)",
            description = "Admin-only worker provisioning endpoint. Requires JWT token with ADMIN role. " +
                    "Admins create worker profiles without a password. " +
                    "Provisioned workers must authenticate separately (admin provides temporary credentials or other flow). " +
                    "This is the admin backend worker creation flow. " +
                    "See POST /auth/register/worker for customer-facing self-registration.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<WorkerResponse>> createWorker(@Valid @RequestBody CreateWorkerRequest request) {
        WorkerResponse worker = workerService.createWorker(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(worker, "Worker created successfully"));
    }

    @GetMapping
    @Operation(
            summary = "List all workers",
            description = "Retrieve a paginated list of all worker profiles",
            security = {}
    )
    public ResponseEntity<ApiResponse<Page<WorkerResponse>>> getAllWorkers(
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(workerService.getAllWorkers(pageable), "Workers fetched successfully"));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get worker by ID",
            description = "Retrieve a specific worker's profile and availability information",
            security = {}
    )
    public ResponseEntity<ApiResponse<WorkerResponse>> getWorkerById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(workerService.getWorkerById(id), "Worker fetched successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Update worker profile",
            description = "Admin endpoint to update worker profile information",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<WorkerResponse>> updateWorker(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkerRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(workerService.updateWorker(id, request), "Worker updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Delete worker",
            description = "Admin endpoint to delete a worker profile",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> deleteWorker(@PathVariable UUID id) {
        workerService.deleteWorker(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Worker deleted successfully"));
    }
}
