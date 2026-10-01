package com.l2c.nexus.identity.security;

import com.l2c.nexus.identity.domain.PasswordPolicy;
import com.l2c.nexus.identity.domain.User;
import com.l2c.nexus.identity.domain.UserStatus;
import com.l2c.nexus.identity.persistence.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Check order matters:
 *
 * <ol>
 *   <li>Unknown email, wrong password, or disabled account all fail identically.
 *   <li>Only after the correct password is the "email not verified" state revealed.
 * </ol>
 */
@Component
class NexusAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    /** Compared against when no account exists, so unknown emails cost as much as known ones. */
    private final String dummyHash;

    NexusAuthenticationProvider(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        String email = authentication.getName().trim().toLowerCase(Locale.ROOT);
        String rawPassword =
                authentication.getCredentials() == null
                        ? ""
                        : authentication.getCredentials().toString();

        // bcrypt only handles 72 bytes, and no valid password is longer (PasswordPolicy).
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_BYTES) {
            passwordEncoder.matches("x", dummyHash);
            throw invalid();
        }

        Optional<User> found = users.findByEmailCaseInsensitive(email);
        if (found.isEmpty()) {
            passwordEncoder.matches(rawPassword, dummyHash);
            throw invalid();
        }

        User user = found.get();
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw invalid();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw invalid();
        }
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

        return UsernamePasswordAuthenticationToken.authenticated(
                AuthenticatedUser.from(user),
                null,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static BadCredentialsException invalid() {
        return new BadCredentialsException("Invalid credentials");
    }
}
