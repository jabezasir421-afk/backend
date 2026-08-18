package com.bluecollar.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID actorUserId,
        String actorRole,
        String action,
        String entityType,
        UUID entityId,
        Instant createdAt,
        String correlationId
) {
}
