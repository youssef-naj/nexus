package com.l2c.nexus.platform.application;

import com.l2c.nexus.identity.application.UserDirectory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PlatformAccessService {

    private final UserDirectory users;

    public PlatformAccessService(UserDirectory users) {
        this.users = users;
    }

    /** Empty unless the account is active, verified and holds the platform role right now. */
    public Optional<PlatformAdmin> resolve(UUID userId) {
        return users.isPlatformAdmin(userId)
                ? Optional.of(new PlatformAdmin(userId))
                : Optional.empty();
    }
}
