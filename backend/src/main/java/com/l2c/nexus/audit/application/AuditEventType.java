package com.l2c.nexus.audit.application;

import java.util.Set;

/**
 * Every auditable event, with the only metadata keys it may carry. Adding a key here is a
 * deliberate, reviewed decision: nothing else can reach the log (ADR-0012, ADR-0017).
 */
public enum AuditEventType {
    USER_EMAIL_VERIFIED(),
    ORGANIZATION_CREATED("name", "slug"),
    INVITATION_CREATED("role"),
    INVITATION_REVOKED("role"),
    INVITATION_ACCEPTED("role"),
    INVITATION_REJECTED("role"),
    MEMBER_ROLE_CHANGED("fromRole", "toRole"),
    MEMBER_REMOVED("role"),
    MEMBER_LEFT("role");

    private final Set<String> allowedMetadataKeys;

    AuditEventType(String... allowedMetadataKeys) {
        this.allowedMetadataKeys = Set.of(allowedMetadataKeys);
    }

    public Set<String> allowedMetadataKeys() {
        return allowedMetadataKeys;
    }
}
