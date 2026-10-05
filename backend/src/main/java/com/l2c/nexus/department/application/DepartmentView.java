package com.l2c.nexus.department.application;

import com.l2c.nexus.department.domain.Department;
import java.time.Instant;
import java.util.UUID;

public record DepartmentView(
        UUID id,
        String name,
        String description,
        boolean active,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static DepartmentView from(Department department) {
        return new DepartmentView(
                department.getId(),
                department.getName(),
                department.getDescription(),
                department.isActive(),
                department.getVersion(),
                department.getCreatedAt(),
                department.getUpdatedAt());
    }
}
