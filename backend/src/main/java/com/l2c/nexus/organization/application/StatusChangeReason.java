package com.l2c.nexus.organization.application;

import com.l2c.nexus.organization.domain.OrganizationStatus;
import java.util.EnumSet;
import java.util.Set;

/**
 * Why a platform administrator suspended or reactivated an organization. A fixed list on purpose:
 * the code goes into the audit log, and nothing typed by a person can end up there (ADR-0032).
 */
public enum StatusChangeReason {
    POLICY_VIOLATION(OrganizationStatus.SUSPENDED),
    SECURITY_CONCERN(OrganizationStatus.SUSPENDED),
    NON_PAYMENT(OrganizationStatus.SUSPENDED),
    ISSUE_RESOLVED(OrganizationStatus.ACTIVE),
    SUSPENDED_IN_ERROR(OrganizationStatus.ACTIVE),
    CUSTOMER_REQUEST(OrganizationStatus.SUSPENDED, OrganizationStatus.ACTIVE),
    OTHER(OrganizationStatus.SUSPENDED, OrganizationStatus.ACTIVE);

    private final Set<OrganizationStatus> targets;

    StatusChangeReason(OrganizationStatus first, OrganizationStatus... rest) {
        this.targets = EnumSet.of(first, rest);
    }

    /** True if this reason may be given when moving an organization TO the given status. */
    public boolean allowedFor(OrganizationStatus target) {
        return targets.contains(target);
    }
}
