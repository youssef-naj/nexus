package com.l2c.nexus.request.domain;

import static com.l2c.nexus.request.domain.RequestStatus.APPROVED;
import static com.l2c.nexus.request.domain.RequestStatus.CHANGES_REQUESTED;
import static com.l2c.nexus.request.domain.RequestStatus.DRAFT;
import static com.l2c.nexus.request.domain.RequestStatus.REJECTED;
import static com.l2c.nexus.request.domain.RequestStatus.SUBMITTED;

import java.util.EnumSet;
import java.util.Set;

/**
 * The workflow (ADR-0010, ADR-0028): DRAFT -> SUBMITTED -> APPROVED | REJECTED, and SUBMITTED ->
 * CHANGES_REQUESTED -> SUBMITTED. The only place that says which action is possible from which
 * status.
 */
public enum RequestAction {
    SUBMIT(EnumSet.of(DRAFT, CHANGES_REQUESTED), SUBMITTED, false),
    APPROVE(EnumSet.of(SUBMITTED), APPROVED, false),
    REJECT(EnumSet.of(SUBMITTED), REJECTED, true),
    REQUEST_CHANGES(EnumSet.of(SUBMITTED), CHANGES_REQUESTED, true);

    private final Set<RequestStatus> allowedFrom;
    private final RequestStatus target;
    private final boolean commentRequired;

    RequestAction(Set<RequestStatus> allowedFrom, RequestStatus target, boolean commentRequired) {
        this.allowedFrom = allowedFrom;
        this.target = target;
        this.commentRequired = commentRequired;
    }

    public boolean allowedFrom(RequestStatus status) {
        return allowedFrom.contains(status);
    }

    public RequestStatus target() {
        return target;
    }

    public boolean commentRequired() {
        return commentRequired;
    }

    /** Review decisions are made by someone other than the creator. */
    public boolean isReview() {
        return this != SUBMIT;
    }
}
