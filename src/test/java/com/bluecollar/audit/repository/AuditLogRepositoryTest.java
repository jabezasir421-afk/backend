package com.bluecollar.audit.repository;

import com.bluecollar.audit.entity.AuditLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditLogRepositoryTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void saveAndFindByIdShouldPersistAuditLog() {
        UUID actorUserId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        AuditLog auditLog = auditLogRepository.saveAndFlush(AuditLog.builder()
                .actorUserId(actorUserId)
                .actorRole("ADMIN")
                .action("USER_DEACTIVATED")
                .entityType("USER")
                .entityId(entityId)
                .oldValue(Map.of("active", true))
                .newValue(Map.of("active", false))
                .ipAddress("127.0.0.1")
                .correlationId("corr-789")
                .build());

        Optional<AuditLog> found = auditLogRepository.findById(auditLog.getId());

        assertTrue(found.isPresent());
        assertEquals("USER_DEACTIVATED", found.get().getAction());
        assertEquals(entityId, found.get().getEntityId());
        assertEquals(actorUserId, found.get().getActorUserId());
    }

    @Test
    void findByEntityTypeAndEntityIdShouldReturnMatchingLogs() {
        UUID entityId = UUID.randomUUID();

        auditLogRepository.saveAndFlush(AuditLog.builder()
                .actorUserId(UUID.randomUUID())
                .actorRole("ADMIN")
                .action("WORKER_VERIFIED")
                .entityType("WORKER")
                .entityId(entityId)
                .build());

        auditLogRepository.saveAndFlush(AuditLog.builder()
                .actorUserId(UUID.randomUUID())
                .actorRole("ADMIN")
                .action("OTHER_ACTION")
                .entityType("USER")
                .entityId(UUID.randomUUID())
                .build());

        Page<AuditLog> page = auditLogRepository.findByEntityTypeAndEntityId(
                "WORKER",
                entityId,
                PageRequest.of(0, 10)
        );

        assertEquals(1, page.getTotalElements());
        assertEquals("WORKER_VERIFIED", page.getContent().getFirst().getAction());
    }

    @Test
    void findByCreatedAtBetweenShouldReturnLogsWithinRange() {
        Instant now = Instant.now();

        auditLogRepository.saveAndFlush(AuditLog.builder()
                .actorUserId(UUID.randomUUID())
                .actorRole("ADMIN")
                .action("BOOKING_CANCELLED")
                .entityType("BOOKING")
                .entityId(UUID.randomUUID())
                .createdAt(now)
                .build());

        Page<AuditLog> page = auditLogRepository.findByCreatedAtBetween(
                now.minusSeconds(60),
                now.plusSeconds(60),
                PageRequest.of(0, 10)
        );

        assertEquals(1, page.getTotalElements());
        assertEquals("BOOKING_CANCELLED", page.getContent().getFirst().getAction());
    }
}
