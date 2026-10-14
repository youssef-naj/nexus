package com.l2c.nexus.identity.application;

import com.l2c.nexus.identity.domain.PlatformRole;
import com.l2c.nexus.identity.domain.User;
import com.l2c.nexus.identity.persistence.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Who holds the platform role. Only the platform synchronizer changes it (ADR-0032). */
@Service
public class PlatformAdminRegistry {

    private final UserRepository users;
    private final Clock clock;

    public PlatformAdminRegistry(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    /** The account with this address, only if it is active and its email is verified. */
    @Transactional(readOnly = true)
    public Optional<UserSummary> eligibleByEmail(String email) {
        return users.findByEmailCaseInsensitive(email.trim().toLowerCase(Locale.ROOT))
                .filter(UserDirectory::usable)
                .map(UserDirectory::summary);
    }

    @Transactional(readOnly = true)
    public List<UUID> currentAdminIds() {
        return users.findByPlatformRole(PlatformRole.PLATFORM_ADMIN).stream()
                .map(User::getId)
                .toList();
    }

    /**
     * Returns true if the role changed. Runs inside the caller's transaction, with its audit row.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean setPlatformAdmin(UUID userId, boolean admin) {
        User user = users.findById(userId).orElseThrow();
        boolean changed = user.setPlatformAdmin(admin, clock.instant());
        if (changed) {
            users.saveAndFlush(user);
        }
        return changed;
    }
}
