package com.l2c.nexus.shared.error;

/** The caller is a member but this action is not allowed for them. Rendered as 403 with a code. */
public class ForbiddenActionException extends RuntimeException {

    private final String code;

    public ForbiddenActionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
