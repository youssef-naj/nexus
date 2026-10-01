package com.l2c.nexus.identity.security;

import org.springframework.security.core.AuthenticationException;

/** Thrown only after the password has been verified, so it reveals nothing to a guesser. */
public class EmailNotVerifiedException extends AuthenticationException {

    public EmailNotVerifiedException() {
        super("Email address not verified");
    }
}
