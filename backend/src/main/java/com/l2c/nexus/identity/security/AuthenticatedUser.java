package com.l2c.nexus.identity.security;

import com.l2c.nexus.identity.domain.User;
import java.io.Serializable;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * What the session remembers about the signed-in user: no password, no hash. getName() is the user
 * ID, which Spring Session stores as the session's principal name, so all sessions of one user can
 * be found and revoked later.
 */
public record AuthenticatedUser(UUID id, String email, String displayName)
        implements AuthenticatedPrincipal, Serializable {

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getDisplayName());
    }

    @Override
    public String getName() {
        return id.toString();
    }
}
