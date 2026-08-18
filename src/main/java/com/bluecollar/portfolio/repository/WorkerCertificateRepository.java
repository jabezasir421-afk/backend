package com.bluecollar.portfolio.repository;

import com.bluecollar.portfolio.entity.WorkerCertificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerCertificateRepository extends JpaRepository<WorkerCertificate, UUID> {

    long countByWorkerIdAndActiveTrue(UUID workerId);

    List<WorkerCertificate> findByWorkerIdAndActiveTrueOrderByCreatedAtDesc(UUID workerId);

    Optional<WorkerCertificate> findByIdAndWorkerIdAndActiveTrue(UUID id, UUID workerId);
}
