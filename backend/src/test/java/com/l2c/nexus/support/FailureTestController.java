package com.l2c.nexus.support;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FailureTestController {

    public static final String SECRET = "SECRET-MARKER-5f3a9c";
    public static final String LEAKED_EMAIL = "leaked.person@example.com";

    @GetMapping("/api/test-support/fail")
    String fail() {
        throw new IllegalStateException(
                "Internal detail that must never reach a client: " + SECRET);
    }

    @GetMapping("/api/test-support/db-detail")
    String databaseDetail() {
        throw new DataIntegrityViolationException(
                "could not execute statement [ERROR: duplicate key value violates unique constraint"
                        + " \"uq_users_email_lower\" Detail: Key (lower(email::text))=("
                        + LEAKED_EMAIL
                        + ") already exists.]");
    }
}
