package com.l2c.nexus.identity.application;

import com.l2c.nexus.identity.domain.User;
import com.l2c.nexus.identity.domain.UserStatus;
import com.l2c.nexus.identity.persistence.UserRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDirectory {

    private final UserRepository users;

    public UserDirectory(UserRepository users) {
        this.users = users;
    }

    /** Any account with this email, whatever its status. */
    @Transactional(readOnly = true)
    public Optional<UserSummary> findByEmail(String email) {
        return users.findByEmailCaseInsensitive(email.trim().toLowerCase(Locale.ROOT))
                .map(UserDirectory::summary);
    }

    /** The account, only if it is active and verified. */
    @Transactional(readOnly = true)
    public Optional<UserSummary> findActiveById(UUID id) {
        return users.findById(id)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE && user.isEmailVerified())
                .map(UserDirectory::summary);
    }

    private static UserSummary summary(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
