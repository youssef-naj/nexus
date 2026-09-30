package com.l2c.nexus.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TokenGeneratorTest {

    private final TokenGenerator generator = new TokenGenerator();

    @Test
    void tokensAreUrlSafeAnd256BitsLong() {
        assertThat(generator.newToken()).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void tokensDoNotRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertThat(seen.add(generator.newToken())).isTrue();
        }
    }

    @Test
    void hashIsDeterministicHexAndDiffersFromTheToken() {
        String token = generator.newToken();

        assertThat(generator.hash(token)).hasSize(64).isEqualTo(generator.hash(token));
        assertThat(generator.hash(token)).isNotEqualTo(token);
    }
}
