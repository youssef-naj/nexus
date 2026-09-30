package com.l2c.nexus.identity.domain;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Length-based password rules: a minimum length, and a maximum that matches bcrypt's 72-byte input
 * limit. No composition rules (digits, symbols), which add friction without adding much strength. A
 * common-password check is a planned hardening step.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    public List<String> violations(String password) {
        List<String> violations = new ArrayList<>();
        if (password.length() < MIN_LENGTH) {
            violations.add("Password must be at least " + MIN_LENGTH + " characters long.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            violations.add("Password is too long (maximum " + MAX_BYTES + " bytes).");
        }
        return violations;
    }
}
