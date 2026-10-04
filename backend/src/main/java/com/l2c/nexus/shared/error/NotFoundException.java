package com.l2c.nexus.shared.error;

/** The requested thing does not exist, or the caller may not know that it does. Rendered as 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException() {
        super("Not found");
    }
}
