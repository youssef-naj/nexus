package com.l2c.nexus.request.api;

import com.l2c.nexus.request.domain.RequestCategory;
import com.l2c.nexus.request.domain.RequestStatus;
import com.l2c.nexus.request.persistence.RequestSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RequestSummaryResponse(
        UUID id,
        String reference,
        String title,
        RequestCategory category,
        RequestStatus status,
        UUID createdByMembershipId,
        String createdByName,
        UUID departmentId,
        String departmentName,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    static RequestSummaryResponse from(RequestSummary summary) {
        return new RequestSummaryResponse(
                summary.id(),
                summary.reference(),
                summary.title(),
                summary.category(),
                summary.status(),
                summary.createdByMembershipId(),
                summary.createdByName(),
                summary.departmentId(),
                summary.departmentName(),
                summary.dueDate(),
                summary.createdAt(),
                summary.updatedAt(),
                summary.version());
    }
}
