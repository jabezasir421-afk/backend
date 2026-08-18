package com.bluecollar.audit.repository;

import com.bluecollar.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId, Pageable pageable);

    Page<AuditLog> findByActorUserId(UUID actorUserId, Pageable pageable);

    Page<AuditLog> findByCreatedAtBetween(Instant from, Instant to, Pageable pageable);

    Page<AuditLog> findByEntityTypeAndEntityIdAndCreatedAtBetween(
            String entityType,
            UUID entityId,
            Instant from,
            Instant to,
            Pageable pageable
    );

    Page<AuditLog> findByActorUserIdAndCreatedAtBetween(
            UUID actorUserId,
            Instant from,
            Instant to,
            Pageable pageable
    );
}
