package com.l2c.nexus.request.persistence;

import com.l2c.nexus.request.domain.RequestCategory;
import com.l2c.nexus.request.domain.RequestStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RequestDetail(
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
        long version) {}
