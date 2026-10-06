package com.l2c.nexus.department.api;

import com.l2c.nexus.department.persistence.DepartmentMemberRepository.DepartmentRef;
import java.util.UUID;

public record MemberDepartmentResponse(UUID id, String name, boolean active) {

    static MemberDepartmentResponse from(DepartmentRef ref) {
        return new MemberDepartmentResponse(ref.id(), ref.name(), ref.active());
    }
}
