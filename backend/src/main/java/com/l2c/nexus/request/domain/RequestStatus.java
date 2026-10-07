package com.l2c.nexus.request.domain;

public enum RequestStatus {
    DRAFT,
    SUBMITTED,
    CHANGES_REQUESTED,
    APPROVED,
    REJECTED;

    /** Only the creator can edit, and only while the request is a draft or sent back. */
    public boolean isEditable() {
        return this == DRAFT || this == CHANGES_REQUESTED;
    }
}
