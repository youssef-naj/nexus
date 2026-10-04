package com.l2c.nexus.team.api;

import com.l2c.nexus.membership.domain.OrgRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** The version is the one the client saw; a mismatch means someone else changed the member. */
public record ChangeRoleRequest(@NotNull OrgRole role, @NotNull @PositiveOrZero Long version) {}
