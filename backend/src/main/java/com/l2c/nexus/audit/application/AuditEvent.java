package com.l2c.nexus.audit.application;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One thing that happened. organizationId is null for platform-level events and actorUserId is null
 * for system actions. Metadata is limited to the keys the event type allows.
 */
public record AuditEvent(
        UUID organizationId,
        UUID actorUserId,
        AuditEventType type,
        AuditTargetType targetType,
        UUID targetId,
        Map<String, String> metadata) {

    public static final int MAX_METADATA_VALUE_LENGTH = 500;

    public AuditEvent {
        Objects.requireNonNull(type, "type");
        Map<String, String> copy = new TreeMap<>(metadata == null ? Map.of() : metadata);
        for (Map.Entry<String, String> entry : copy.entrySet()) {
            if (!type.allowedMetadataKeys().contains(entry.getKey())) {
                throw new IllegalArgumentException(
                        "Metadata key '" + entry.getKey() + "' is not allowed for " + type);
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException(
                        "Metadata value for '" + entry.getKey() + "' must not be null");
            }
            if (entry.getValue().length() > MAX_METADATA_VALUE_LENGTH) {
                throw new IllegalArgumentException(
                        "Metadata value for '" + entry.getKey() + "' is too long");
            }
        }
        metadata = Collections.unmodifiableMap(copy);
    }

    public static AuditEvent of(
            AuditEventType type, UUID actorUserId, AuditTargetType targetType, UUID targetId) {
        return new AuditEvent(null, actorUserId, type, targetType, targetId, Map.of());
    }

    public AuditEvent inOrganization(UUID organizationId) {
        return new AuditEvent(organizationId, actorUserId, type, targetType, targetId, metadata);
    }

    public AuditEvent withMetadata(Map<String, String> metadata) {
        return new AuditEvent(organizationId, actorUserId, type, targetType, targetId, metadata);
    }
}
