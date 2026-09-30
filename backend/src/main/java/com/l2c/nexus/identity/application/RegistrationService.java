package com.l2c.nexus.identity.application;

import com.l2c.nexus.identity.domain.PasswordPolicy;
import com.l2c.nexus.identity.domain.User;
import com.l2c.nexus.identity.persistence.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_users_email_lower";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final Clock clock;

    public RegistrationService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.clock = clock;
    }

    /**
     * Registers a user, or reports that the email is already taken. The caller must respond the
     * same way for both outcomes so the endpoint cannot be used to discover which emails exist.
     *
     * <p>Not {@code @Transactional} on purpose: {@code saveAndFlush} runs in its own transaction,
     * so a duplicate-key failure can be handled here without leaving an outer transaction
     * rollback-only.
     */
    public RegistrationOutcome register(RegisterUserCommand command) {
        List<String> violations = passwordPolicy.violations(command.password());
        if (!violations.isEmpty()) {
            throw new WeakPasswordException(violations);
        }

        String email = command.email().trim().toLowerCase(Locale.ROOT);
        String displayName = command.displayName().trim();

        // Hash before checking for duplicates so both outcomes cost the same time.
        String passwordHash = passwordEncoder.encode(command.password());

        if (users.findByEmailCaseInsensitive(email).isPresent()) {
            return RegistrationOutcome.ALREADY_REGISTERED;
        }
        try {
            users.saveAndFlush(User.register(email, passwordHash, displayName, clock.instant()));
            return RegistrationOutcome.CREATED;
        } catch (DataIntegrityViolationException e) {
            // A concurrent registration won the race: the unique index caught it.
            if (isDuplicateEmail(e)) {
                return RegistrationOutcome.ALREADY_REGISTERED;
            }
            throw e;
        }
    }

    private static boolean isDuplicateEmail(DataIntegrityViolationException e) {
        String message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        return message != null && message.contains(EMAIL_UNIQUE_CONSTRAINT);
    }
}
