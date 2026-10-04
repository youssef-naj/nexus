package com.l2c.nexus.team.api;

import com.l2c.nexus.membership.domain.OrgRole;
import com.l2c.nexus.team.application.InvitationView;
import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
        UUID id,
        String email,
        OrgRole role,
        Instant createdAt,
        Instant expiresAt,
        boolean expired) {

    static InvitationResponse from(InvitationView view) {
        return new InvitationResponse(
                view.id(),
                view.email(),
                view.role(),
                view.createdAt(),
                view.expiresAt(),
                view.expired());
    }
}
