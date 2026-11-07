package com.bluecollar.notification.repository;

import com.bluecollar.notification.entity.EmailOutbox;
import com.bluecollar.notification.entity.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    List<EmailOutbox> findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    @Query("""
            SELECT e FROM EmailOutbox e
            WHERE e.status = :status
              AND (e.claimedAt IS NULL OR e.claimedAt < :staleThreshold)
            ORDER BY e.createdAt ASC
            """)
    List<EmailOutbox> findClaimableEmails(
            @Param("status") OutboxStatus status,
            @Param("staleThreshold") Instant staleThreshold,
            Pageable pageable
    );

    @Modifying
    @Query("""
            UPDATE EmailOutbox e
            SET e.claimedBy = :instanceId, e.claimedAt = :now
            WHERE e.id = :id
              AND e.status = :status
              AND (e.claimedAt IS NULL OR e.claimedAt < :staleThreshold)
            """)
    int claimMessage(
            @Param("id") UUID id,
            @Param("instanceId") String instanceId,
            @Param("now") Instant now,
            @Param("status") OutboxStatus status,
            @Param("staleThreshold") Instant staleThreshold
    );
}
