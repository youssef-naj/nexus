package com.l2c.nexus.membership.domain;

/** A user's role in ONE organization. The same user can hold different roles elsewhere. */
public enum OrgRole {
    OWNER,
    ADMIN,
    MANAGER,
    EMPLOYEE
}
