package com.l2c.nexus.shared.logging;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Describes an exception for the logs WITHOUT its message (ADR-0033). Exception messages can carry
 * data values: PostgreSQL's duplicate-key error contains the duplicate email, SMTP errors contain
 * addresses, JSON parse errors contain pieces of the request. This writes only class names, the
 * SQLSTATE code, and the first stack frames of the deepest cause.
 */
public final class SafeExceptionFormatter {

    static final int MAX_CAUSES = 8;
    static final int MAX_FRAMES = 12;

    private SafeExceptionFormatter() {}

    public static String format(Throwable throwable) {
        if (throwable == null) {
            return "no exception";
        }
        StringBuilder out = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable;
        Throwable deepest = throwable;
        int depth = 0;
        while (current != null && depth < MAX_CAUSES && seen.add(current)) {
            if (depth > 0) {
                out.append(" <- caused by ");
            }
            out.append(current.getClass().getName());
            if (current instanceof SQLException sql && sql.getSQLState() != null) {
                out.append(" [sqlState=").append(sql.getSQLState()).append(']');
            }
            deepest = current;
            current = current.getCause();
            depth++;
        }
        if (current != null && !seen.contains(current)) {
            out.append(" <- ...");
        }
        StackTraceElement[] frames = deepest.getStackTrace();
        for (int i = 0; i < Math.min(frames.length, MAX_FRAMES); i++) {
            out.append(" | at ").append(frames[i]);
        }
        return out.toString();
    }
}
