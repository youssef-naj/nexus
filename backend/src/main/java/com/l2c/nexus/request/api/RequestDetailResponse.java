package com.l2c.nexus.request.api;

import com.l2c.nexus.request.domain.RequestAction;
import com.l2c.nexus.request.domain.RequestCategory;
import com.l2c.nexus.request.domain.RequestStatus;
import com.l2c.nexus.request.persistence.RequestDetail;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * "editable" and "actions" tell the UI what THIS viewer may do now. They are hints computed by the
 * server; every action is authorized again when it is used.
 */
public record RequestDetailResponse(
        UUID id,
        String reference,
        String title,
        String description,
        RequestCategory category,
        RequestStatus status,
        UUID createdByMembershipId,
        String createdByName,
        UUID assigneeMembershipId,
        String assigneeName,
        UUID departmentId,
        String departmentName,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt,
        long version,
        boolean editable,
        List<RequestAction> actions) {

    static RequestDetailResponse from(
            RequestDetail detail, UUID viewerMembershipId, List<RequestAction> actions) {
        boolean editable =
                detail.createdByMembershipId().equals(viewerMembershipId)
                        && detail.status().isEditable();
        return new RequestDetailResponse(
                detail.id(),
                detail.reference(),
                detail.title(),
                detail.description(),
                detail.category(),
                detail.status(),
                detail.createdByMembershipId(),
                detail.createdByName(),
                detail.assigneeMembershipId(),
                detail.assigneeName(),
                detail.departmentId(),
                detail.departmentName(),
                detail.dueDate(),
                detail.createdAt(),
                detail.updatedAt(),
                detail.version(),
                editable,
                actions);
    }
}
