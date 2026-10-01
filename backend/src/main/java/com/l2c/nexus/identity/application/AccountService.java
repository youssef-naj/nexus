package com.l2c.nexus.identity.application;

import com.l2c.nexus.identity.domain.User;
import com.l2c.nexus.identity.domain.UserStatus;
import com.l2c.nexus.identity.persistence.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final UserRepository users;

    public AccountService(UserRepository users) {
        this.users = users;
    }

    /** The user, only if the account is still active and verified. */
    @Transactional(readOnly = true)
    public Optional<User> findActiveUser(UUID id) {
        return users.findById(id)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE && user.isEmailVerified());
    }
}
