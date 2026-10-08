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
    MEMBER_LEFT("role"),
    DEPARTMENT_CREATED("name"),
    DEPARTMENT_UPDATED("fromName", "toName"),
    DEPARTMENT_DEACTIVATED(),
    DEPARTMENT_REACTIVATED(),
    DEPARTMENT_MEMBER_ADDED("membershipId"),
    DEPARTMENT_MEMBER_REMOVED("membershipId"),
    REQUEST_SUBMITTED("reference"),
    REQUEST_APPROVED("reference"),
    REQUEST_REJECTED("reference"),
    REQUEST_CHANGES_REQUESTED("reference");

    private final Set<String> allowedMetadataKeys;

    AuditEventType(String... allowedMetadataKeys) {
        this.allowedMetadataKeys = Set.of(allowedMetadataKeys);
    }

    public Set<String> allowedMetadataKeys() {
        return allowedMetadataKeys;
    }
}
