package com.l2c.nexus.shared.security;

import java.io.IOException;

/** Thrown while reading a request body that exceeds the configured limit. */
public class RequestBodyTooLargeException extends IOException {

    public RequestBodyTooLargeException(long limit) {
        super("Request body exceeds " + limit + " bytes");
    }
}
