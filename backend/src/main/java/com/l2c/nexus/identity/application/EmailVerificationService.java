package com.l2c.nexus.identity.application;

import com.l2c.nexus.identity.domain.TokenType;
import com.l2c.nexus.identity.domain.User;
import com.l2c.nexus.identity.domain.UserToken;
import com.l2c.nexus.identity.persistence.UserRepository;
import com.l2c.nexus.identity.persistence.UserTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    static final Duration TOKEN_LIFETIME = Duration.ofHours(24);

    private final UserRepository users;
    private final UserTokenRepository tokens;
    private final TokenGenerator generator;
    private final Clock clock;

    public EmailVerificationService(
            UserRepository users,
            UserTokenRepository tokens,
            TokenGenerator generator,
            Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.generator = generator;
        this.clock = clock;
    }

    /** Issues a fresh token and returns the raw value, which exists only in the email. */
    @Transactional
    public String issueFor(UUID userId) {
        Instant now = clock.instant();
        tokens.invalidateOutstanding(userId, TokenType.VERIFY_EMAIL, now);
        String raw = generator.newToken();
        tokens.save(
                UserToken.issue(
                        userId,
                        TokenType.VERIFY_EMAIL,
                        generator.hash(raw),
                        now,
                        now.plus(TOKEN_LIFETIME)));
        return raw;
    }

    @Transactional
    public void verify(String rawToken) {
        Instant now = clock.instant();
        UserToken token =
                tokens.findByTokenHash(generator.hash(rawToken))
                        .filter(t -> t.getType() == TokenType.VERIFY_EMAIL)
                        .orElseThrow(InvalidTokenException::new);

        // One atomic statement decides who wins; unused and unexpired, or nothing.
        if (tokens.markUsed(token.getId(), now) != 1) {
            throw new InvalidTokenException();
        }
        User user = users.findById(token.getUserId()).orElseThrow(InvalidTokenException::new);
        user.markEmailVerified(now);
    }
}
