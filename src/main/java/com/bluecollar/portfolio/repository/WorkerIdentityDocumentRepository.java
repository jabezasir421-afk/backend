package com.bluecollar.portfolio.repository;

import com.bluecollar.portfolio.entity.DocumentType;
import com.bluecollar.portfolio.entity.VerificationStatus;
import com.bluecollar.portfolio.entity.WorkerIdentityDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerIdentityDocumentRepository extends JpaRepository<WorkerIdentityDocument, UUID> {

    long countByWorkerIdAndActiveTrue(UUID workerId);

    boolean existsByWorkerIdAndDocumentTypeAndActiveTrue(UUID workerId, DocumentType documentType);

    List<WorkerIdentityDocument> findByWorkerIdAndActiveTrueOrderByCreatedAtDesc(UUID workerId);

    List<WorkerIdentityDocument> findByVerificationStatusAndActiveTrueOrderByCreatedAtAsc(VerificationStatus status);

    Page<WorkerIdentityDocument> findByVerificationStatusAndActiveTrue(VerificationStatus status, Pageable pageable);

    Optional<WorkerIdentityDocument> findByIdAndActiveTrue(UUID id);

    long countByWorkerIdAndActiveTrueAndVerificationStatusIn(
            UUID workerId,
            List<VerificationStatus> statuses
    );

    boolean existsByWorkerIdAndVerificationStatusAndActiveTrue(UUID workerId, VerificationStatus verificationStatus);
}
