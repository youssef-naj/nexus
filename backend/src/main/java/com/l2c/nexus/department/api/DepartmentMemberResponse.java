package com.l2c.nexus.department.api;

import com.l2c.nexus.department.persistence.DepartmentMemberRepository.MemberRow;
import com.l2c.nexus.membership.domain.OrgRole;
import java.time.Instant;
import java.util.UUID;

/** email is null unless the viewer may manage members. */
public record DepartmentMemberResponse(
        UUID membershipId,
        UUID userId,
        String displayName,
        String email,
        OrgRole role,
        Instant assignedAt) {

    static DepartmentMemberResponse from(MemberRow row) {
        return new DepartmentMemberResponse(
                row.membershipId(),
                row.userId(),
                row.displayName(),
                row.email(),
                row.role(),
                row.assignedAt());
    }
}
