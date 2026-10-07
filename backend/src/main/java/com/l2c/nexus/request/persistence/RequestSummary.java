package com.l2c.nexus.request.persistence;

import com.l2c.nexus.request.domain.RequestCategory;
import com.l2c.nexus.request.domain.RequestStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RequestSummary(
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
        long version) {}
