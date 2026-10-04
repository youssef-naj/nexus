package com.l2c.nexus.team.application;

import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.team.domain.Invitation;
import java.time.Instant;
import java.util.UUID;

public record InvitationView(
        UUID id,
        String email,
        OrgRole role,
        Instant createdAt,
        Instant expiresAt,
        boolean expired) {

    static InvitationView from(Invitation invitation, Instant now) {
        return new InvitationView(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getRole(),
                invitation.getCreatedAt(),
                invitation.getExpiresAt(),
                !invitation.getExpiresAt().isAfter(now));
    }
}
