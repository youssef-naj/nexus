package com.l2c.nexus.team.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InvitationTokenRequest(@NotBlank @Size(max = 200) String token) {

    @Override
    public String toString() {
        return "InvitationTokenRequest[redacted]";
    }
}
