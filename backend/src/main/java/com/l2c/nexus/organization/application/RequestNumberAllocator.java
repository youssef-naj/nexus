package com.l2c.nexus.organization.application;

import com.l2c.nexus.shared.error.NotFoundException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestNumberAllocator {

    private final JdbcClient jdbc;

    public RequestNumberAllocator(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns the next reference such as "REQ-000042". The single UPDATE both increments and
     * returns the counter, and takes the organization's row lock until the caller's transaction
     * ends: concurrent creators queue up, each gets a distinct number, and a rolled-back creation
     * rolls its number back too (no gaps). The counter column is not mapped in the JPA entity, so
     * entity updates never overwrite it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(UUID organizationId) {
        long number =
                jdbc.sql(
                                "UPDATE organizations SET request_counter = request_counter + 1"
                                        + " WHERE id = ? RETURNING request_counter")
                        .param(organizationId)
                        .query(Long.class)
                        .optional()
                        .orElseThrow(NotFoundException::new);
        return format(number);
    }

    static String format(long number) {
        return String.format(Locale.ROOT, "REQ-%06d", number);
    }
}
