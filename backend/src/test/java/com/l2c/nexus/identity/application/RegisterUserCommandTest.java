package com.l2c.nexus.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegisterUserCommandTest {

    @Test
    void toStringNeverExposesThePassword() {
        RegisterUserCommand command =
                new RegisterUserCommand("ada@example.com", "s3cret-passphrase", "Ada");

        assertThat(command.toString()).doesNotContain("s3cret-passphrase", "ada@example.com");
    }
}
