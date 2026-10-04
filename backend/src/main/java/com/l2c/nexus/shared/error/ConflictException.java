package com.l2c.nexus.shared.error;

/** The request conflicts with the current state. Rendered as 409 with a machine-readable code. */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
