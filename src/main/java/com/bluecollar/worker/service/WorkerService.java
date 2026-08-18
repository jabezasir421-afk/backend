package com.bluecollar.worker.service;

import com.bluecollar.worker.dto.CreateWorkerRequest;
import com.bluecollar.worker.dto.UpdateWorkerRequest;
import com.bluecollar.worker.dto.WorkerResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface WorkerService {

    /**
     * Creates a new worker when phone number and email are not already in use.
     */
    WorkerResponse createWorker(CreateWorkerRequest request);

    /**
     * Returns all workers ordered by creation time.
     */
    Page<WorkerResponse> getAllWorkers(Pageable pageable);

    /**
     * Returns a worker by its unique identifier.
     */
    WorkerResponse getWorkerById(UUID id);

    /**
     * Updates a worker when it exists and unique fields do not conflict.
     */
    WorkerResponse updateWorker(UUID id, UpdateWorkerRequest request);

    /**
     * Deletes a worker by its unique identifier.
     */
    void deleteWorker(UUID id);
}
