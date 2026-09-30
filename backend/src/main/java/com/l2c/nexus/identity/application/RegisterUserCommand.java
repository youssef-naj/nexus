package com.l2c.nexus.identity.application;

import java.util.Objects;

public record RegisterUserCommand(String email, String password, String displayName) {

    public RegisterUserCommand {
        Objects.requireNonNull(email);
        Objects.requireNonNull(password);
        Objects.requireNonNull(displayName);
    }

    /** Never print the password (or other personal data) if a command is logged. */
    @Override
    public String toString() {
        return "RegisterUserCommand[redacted]";
    }
}
