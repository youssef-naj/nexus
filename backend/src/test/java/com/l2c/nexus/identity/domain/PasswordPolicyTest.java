package com.l2c.nexus.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsALongEnoughPassword() {
        assertThat(policy.violations("correct horse battery")).isEmpty();
    }

    @Test
    void rejectsAShortPassword() {
        assertThat(policy.violations("short")).hasSize(1);
    }

    @Test
    void acceptsExactlyTheByteLimit() {
        assertThat(policy.violations("a".repeat(72))).isEmpty();
    }

    @Test
    void rejectsOneByteOverTheLimit() {
        assertThat(policy.violations("a".repeat(73))).hasSize(1);
    }

    @Test
    void countsBytesNotCharacters() {
        // 40 characters, but 80 bytes in UTF-8
        assertThat(policy.violations("é".repeat(40))).hasSize(1);
    }
}
