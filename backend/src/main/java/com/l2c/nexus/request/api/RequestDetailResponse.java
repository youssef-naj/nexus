package com.l2c.nexus.request.api;

import com.l2c.nexus.request.domain.RequestCategory;
import com.l2c.nexus.request.domain.RequestStatus;
import com.l2c.nexus.request.persistence.RequestDetail;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** "editable" tells the UI whether THIS viewer may edit now (creator, and a draft or sent back). */
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
        boolean editable) {

    static RequestDetailResponse from(RequestDetail detail, UUID viewerMembershipId) {
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
                editable);
    }
}
