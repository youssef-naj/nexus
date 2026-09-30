package com.l2c.nexus.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(@NotBlank @Size(max = 200) String token) {

    @Override
    public String toString() {
        return "VerifyEmailRequest[redacted]";
    }
}
