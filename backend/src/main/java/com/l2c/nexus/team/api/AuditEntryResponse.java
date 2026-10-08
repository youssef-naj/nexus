package com.l2c.nexus.team.api;

import com.l2c.nexus.team.application.AuditViewerService.Entry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id,
        String eventType,
        UUID actorUserId,
        String actorName,
        String targetType,
        UUID targetId,
        Map<String, String> metadata,
        Instant occurredAt) {

    static AuditEntryResponse from(Entry entry) {
        return new AuditEntryResponse(
                entry.id(),
                entry.eventType(),
                entry.actorUserId(),
                entry.actorName(),
                entry.targetType(),
                entry.targetId(),
                entry.metadata(),
                entry.occurredAt());
    }
}
