package com.bluecollar.admin.service;

import com.bluecollar.portfolio.entity.VerificationStatus;
import com.bluecollar.portfolio.repository.WorkerIdentityDocumentRepository;
import com.bluecollar.worker.dto.WorkerResponse;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.exception.WorkerNotFoundException;
import com.bluecollar.worker.mapper.WorkerMapper;
import com.bluecollar.worker.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminWorkerServiceImpl implements AdminWorkerService {

    private final WorkerRepository workerRepository;
    private final WorkerMapper workerMapper;
    private final WorkerIdentityDocumentRepository identityDocumentRepository;

    @Override
    public WorkerResponse verifyWorker(UUID id) {
        Worker worker = findWorker(id);
        if (!Boolean.TRUE.equals(worker.getActive())) {
            throw new IllegalStateException("Cannot verify an inactive worker");
        }
        boolean hasVerifiedIdentity = identityDocumentRepository
                .existsByWorkerIdAndVerificationStatusAndActiveTrue(worker.getId(), VerificationStatus.VERIFIED);
        if (!hasVerifiedIdentity) {
            throw new IllegalStateException("Worker must have at least one verified identity document");
        }
        worker.setVerified(true);
        return workerMapper.toResponse(workerRepository.save(worker));
    }

    @Override
    public WorkerResponse unverifyWorker(UUID id) {
        Worker worker = findWorker(id);
        worker.setVerified(false);
        return workerMapper.toResponse(workerRepository.save(worker));
    }

    @Override
    public WorkerResponse deactivateWorker(UUID id) {
        Worker worker = findWorker(id);
        worker.setActive(false);
        worker.setAvailable(false);
        return workerMapper.toResponse(workerRepository.save(worker));
    }

    private Worker findWorker(UUID id) {
        return workerRepository.findById(id)
                .orElseThrow(() -> new WorkerNotFoundException(id));
    }
}
