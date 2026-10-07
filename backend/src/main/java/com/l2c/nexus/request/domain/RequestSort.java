package com.l2c.nexus.request.domain;

/** The only columns a client may sort by. A whitelist keeps user input out of the query text. */
public enum RequestSort {
    CREATED,
    UPDATED,
    DUE_DATE
}
