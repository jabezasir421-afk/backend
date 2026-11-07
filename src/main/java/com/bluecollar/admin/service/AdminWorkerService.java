package com.bluecollar.admin.service;

import com.bluecollar.admin.dto.AdminIdentityDocumentResponse;
import com.bluecollar.admin.dto.LinkWorkerUserRequest;
import com.bluecollar.admin.dto.RejectDocumentRequest;
import com.bluecollar.worker.dto.WorkerResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AdminWorkerService {

    WorkerResponse verifyWorker(UUID id);

    WorkerResponse unverifyWorker(UUID id);

    WorkerResponse deactivateWorker(UUID id);

    WorkerResponse linkUserAccount(UUID workerId, LinkWorkerUserRequest request);

    Page<AdminIdentityDocumentResponse> listPendingIdentityDocuments(Pageable pageable);

    List<AdminIdentityDocumentResponse> getWorkerIdentityDocuments(UUID workerId);

    AdminIdentityDocumentResponse approveIdentityDocument(UUID documentId);

    AdminIdentityDocumentResponse rejectIdentityDocument(UUID documentId, RejectDocumentRequest request);
}
