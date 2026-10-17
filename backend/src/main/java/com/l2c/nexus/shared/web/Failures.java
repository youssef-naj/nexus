package com.l2c.nexus.shared.web;

import com.l2c.nexus.shared.logging.SafeExceptionFormatter;
import com.l2c.nexus.shared.security.RequestBodyTooLargeException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.UUID;
import org.hibernate.exception.JDBCConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Turns an unexpected exception into a safe response and a safe log line (ADR-0033). Used by the
 * controller error handler and by the filter that catches failures outside controllers.
 */
public final class Failures {

    public record Failure(int status, String title, String detail, Long retryAfterSeconds) {}

    public static final Failure TOO_LARGE =
            new Failure(413, "Payload too large", "The request body is too large.", null);
    public static final Failure UNAVAILABLE =
            new Failure(
                    503,
                    "Service unavailable",
                    "The service is temporarily unavailable. Please try again shortly.",
                    5L);
    public static final Failure INTERNAL =
            new Failure(500, "Internal server error", "An unexpected error occurred.", null);

    private static final Logger log = LoggerFactory.getLogger(Failures.class);
    private static final int MAX_DEPTH = 10;

    private Failures() {}

    /** Finds the real cause behind wrappers (servlet, transaction, framework) and classifies it. */
    public static Failure classify(Throwable throwable) {
        int depth = 0;
        for (Throwable cause = throwable;
                cause != null && depth < MAX_DEPTH;
                cause = cause.getCause()) {
            depth++;
            if (cause instanceof RequestBodyTooLargeException) {
                return TOO_LARGE;
            }
            if (cause instanceof DataAccessResourceFailureException
                    || cause instanceof CannotCreateTransactionException
                    || cause instanceof QueryTimeoutException
                    || cause instanceof JDBCConnectionException
                    || cause instanceof SQLTransientConnectionException
                    || connectionProblem(cause)) {
                return UNAVAILABLE;
            }
        }
        return INTERNAL;
    }

    /**
     * PostgreSQL SQLSTATE classes for "cannot connect", "server shutting down" and "out of
     * resources".
     */
    private static boolean connectionProblem(Throwable cause) {
        if (!(cause instanceof SQLException sql) || sql.getSQLState() == null) {
            return false;
        }
        String state = sql.getSQLState();
        return state.startsWith("08") || state.startsWith("57P0") || state.startsWith("53");
    }

    /** Logs a 5xx failure safely (no messages, one line) and returns the incident reference. */
    public static String report(Failure failure, Throwable cause) {
        String incident = UUID.randomUUID().toString().substring(0, 8);
        if (failure.status() == 503) {
            log.warn(
                    "Service unavailable, incident {}: {}",
                    incident,
                    SafeExceptionFormatter.format(cause));
        } else if (failure.status() >= 500) {
            log.error(
                    "Unhandled failure, incident {}: {}",
                    incident,
                    SafeExceptionFormatter.format(cause));
        }
        return incident;
    }

    /** The response body as JSON, for code that runs outside Spring MVC (filters). */
    public static String problemJson(Failure failure, String incident) {
        StringBuilder json =
                new StringBuilder("{\"type\":\"about:blank\",\"title\":\"")
                        .append(failure.title())
                        .append("\",\"status\":")
                        .append(failure.status())
                        .append(",\"detail\":\"")
                        .append(failure.detail())
                        .append('"');
        if (failure.status() >= 500) {
            json.append(",\"incidentId\":\"").append(incident).append('"');
        }
        return json.append('}').toString();
    }
}
