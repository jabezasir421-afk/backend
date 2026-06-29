package com.bluecollar.admin.service;

import com.bluecollar.worker.dto.WorkerResponse;

import java.util.UUID;

public interface AdminWorkerService {

    WorkerResponse verifyWorker(UUID id);

    WorkerResponse unverifyWorker(UUID id);

    WorkerResponse deactivateWorker(UUID id);
}
