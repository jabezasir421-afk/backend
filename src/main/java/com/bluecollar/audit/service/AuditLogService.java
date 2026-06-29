package com.bluecollar.audit.service;

import com.bluecollar.audit.dto.AuditLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface AuditLogService {

    void logAsync(
            UUID actorUserId,
            String actorRole,
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> oldValue,
            Map<String, Object> newValue,
            String ipAddress,
            String correlationId
    );

    Page<AuditLogResponse> search(
            String entityType,
            UUID entityId,
            UUID actorUserId,
            Instant from,
            Instant to,
            Pageable pageable
    );
}
