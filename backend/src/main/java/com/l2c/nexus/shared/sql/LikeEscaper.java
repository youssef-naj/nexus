package com.l2c.nexus.shared.sql;

/**
 * Makes %, _ and \ match themselves in a LIKE pattern, so searching "100%" does not match
 * everything.
 */
public final class LikeEscaper {

    private LikeEscaper() {}

    public static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
