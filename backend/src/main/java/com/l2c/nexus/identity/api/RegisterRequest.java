package com.l2c.nexus.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 1000) String password,
        @NotBlank @Size(max = 120) String displayName) {

    /** Never print credentials if a request is logged. */
    @Override
    public String toString() {
        return "RegisterRequest[redacted]";
    }
}
