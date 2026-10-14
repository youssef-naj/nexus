package com.l2c.nexus.platform.api;

import com.l2c.nexus.organization.application.StatusChangeReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** The reason is a code from a fixed list; the version is the one the administrator saw. */
public record StatusChangePayload(
        @NotNull StatusChangeReason reason, @NotNull @PositiveOrZero Long version) {}
