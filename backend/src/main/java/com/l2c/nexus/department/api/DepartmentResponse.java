package com.l2c.nexus.department.api;

import com.l2c.nexus.department.application.DepartmentView;
import java.time.Instant;
import java.util.UUID;

public record DepartmentResponse(
        UUID id,
        String name,
        String description,
        boolean active,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static DepartmentResponse from(DepartmentView view) {
        return new DepartmentResponse(
                view.id(),
                view.name(),
                view.description(),
                view.active(),
                view.version(),
                view.createdAt(),
                view.updatedAt());
    }
}
