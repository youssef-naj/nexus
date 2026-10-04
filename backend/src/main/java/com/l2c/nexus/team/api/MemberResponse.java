package com.l2c.nexus.team.api;

import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.team.application.MemberDirectoryService.MemberListItem;
import java.time.Instant;
import java.util.UUID;

/** email is null unless the viewer may manage members. "you" marks the caller's own row. */
public record MemberResponse(
        UUID id,
        UUID userId,
        String displayName,
        String email,
        OrgRole role,
        Instant joinedAt,
        long version,
        boolean you) {

    static MemberResponse from(MemberListItem item, UUID currentUserId) {
        return new MemberResponse(
                item.membershipId(),
                item.userId(),
                item.displayName(),
                item.email(),
                item.role(),
                item.joinedAt(),
                item.version(),
                item.userId().equals(currentUserId));
    }
}
