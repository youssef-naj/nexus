package com.l2c.nexus.team.api;

import com.l2c.nexus.membership.domain.OrgRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InvitationRequest(
        @NotBlank @Email @Size(max = 320) String email, @NotNull OrgRole role) {

    @Override
    public String toString() {
        return "InvitationRequest[redacted]";
    }
}
