package com.bluecollar.worker.controller;

import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.worker.dto.CreateWorkerRequest;
import com.bluecollar.worker.dto.UpdateWorkerRequest;
import com.bluecollar.worker.dto.WorkerResponse;
import com.bluecollar.worker.service.WorkerService;
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
public class WorkerController {

    private final WorkerService workerService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<WorkerResponse>> createWorker(@Valid @RequestBody CreateWorkerRequest request) {
        WorkerResponse worker = workerService.createWorker(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(worker, "Worker created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<WorkerResponse>>> getAllWorkers(
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(workerService.getAllWorkers(pageable), "Workers fetched successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkerResponse>> getWorkerById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(workerService.getWorkerById(id), "Worker fetched successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<WorkerResponse>> updateWorker(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkerRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(workerService.updateWorker(id, request), "Worker updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteWorker(@PathVariable UUID id) {
        workerService.deleteWorker(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Worker deleted successfully"));
    }
}
