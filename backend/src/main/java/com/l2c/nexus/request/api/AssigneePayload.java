package com.l2c.nexus.request.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

/** The version is the one the user saw. To remove the assignee, use DELETE instead. */
public record AssigneePayload(@NotNull UUID membershipId, @NotNull @PositiveOrZero Long version) {}
