package com.l2c.nexus.identity.persistence;

import com.l2c.nexus.identity.domain.TokenType;
import com.l2c.nexus.identity.domain.UserToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

    Optional<UserToken> findByTokenHash(String tokenHash);

    /** Invalidates every unused token of this type for the user. */
    @Modifying
    @Query(
            "update UserToken t set t.usedAt = :now"
                    + " where t.userId = :userId and t.type = :type and t.usedAt is null")
    int invalidateOutstanding(
            @Param("userId") UUID userId, @Param("type") TokenType type, @Param("now") Instant now);

    /**
     * Atomically consumes a token. Returns 1 only for the single caller that finds it unused and
     * unexpired, so two simultaneous requests cannot both succeed.
     */
    @Modifying
    @Query(
            "update UserToken t set t.usedAt = :now"
                    + " where t.id = :id and t.usedAt is null and t.expiresAt > :now")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);
}
