package com.bluecollar.audit.service;

import com.bluecollar.audit.dto.AuditLogResponse;
import com.bluecollar.audit.entity.AuditLog;
import com.bluecollar.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    private UUID actorUserId;
    private UUID entityId;
    private AuditLog auditLog;

    @BeforeEach
    void setUp() {
        actorUserId = UUID.randomUUID();
        entityId = UUID.randomUUID();
        auditLog = AuditLog.builder()
                .actorUserId(actorUserId)
                .actorRole("ADMIN")
                .action("USER_ACTIVATED")
                .entityType("USER")
                .entityId(entityId)
                .oldValue(Map.of("active", false))
                .newValue(Map.of("active", true))
                .ipAddress("127.0.0.1")
                .correlationId("corr-123")
                .createdAt(Instant.parse("2026-06-28T10:00:00Z"))
                .build();
        auditLog.setId(UUID.randomUUID());
    }

    @Test
    void searchShouldReturnFilteredAuditLogs() {
        PageRequest pageable = PageRequest.of(0, 10);
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");
        Page<AuditLog> page = new PageImpl<>(List.of(auditLog), pageable, 1);

        when(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        Page<AuditLogResponse> result = auditLogService.search(
                "USER",
                entityId,
                actorUserId,
                from,
                to,
                pageable
        );

        assertEquals(1, result.getTotalElements());
        AuditLogResponse response = result.getContent().getFirst();
        assertEquals(auditLog.getId(), response.id());
        assertEquals(actorUserId, response.actorUserId());
        assertEquals("ADMIN", response.actorRole());
        assertEquals("USER_ACTIVATED", response.action());
        assertEquals("USER", response.entityType());
        assertEquals(entityId, response.entityId());
        assertEquals("corr-123", response.correlationId());
        verify(auditLogRepository).findAll(any(Specification.class), any(PageRequest.class));
    }

    @Test
    void logAsyncShouldPersistAuditLogEntry() {
        Map<String, Object> oldValue = Map.of("active", false);
        Map<String, Object> newValue = Map.of("active", true);

        auditLogService.logAsync(
                actorUserId,
                "ADMIN",
                "USER_ACTIVATED",
                "USER",
                entityId,
                oldValue,
                newValue,
                "127.0.0.1",
                "corr-456"
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals(actorUserId, saved.getActorUserId());
        assertEquals("ADMIN", saved.getActorRole());
        assertEquals("USER_ACTIVATED", saved.getAction());
        assertEquals("USER", saved.getEntityType());
        assertEquals(entityId, saved.getEntityId());
        assertEquals(oldValue, saved.getOldValue());
        assertEquals(newValue, saved.getNewValue());
        assertEquals("127.0.0.1", saved.getIpAddress());
        assertEquals("corr-456", saved.getCorrelationId());
    }
}
