package com.l2c.nexus.department.domain;

/** The only columns a client may sort by. A whitelist keeps user input out of the query text. */
public enum DepartmentSort {
    NAME,
    CREATED
}
