package com.l2c.nexus.request.api;

import com.l2c.nexus.request.domain.RequestAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** The version is the one the user saw; a mismatch means the request changed under them. */
public record TransitionPayload(
        @NotNull RequestAction action,
        @NotNull @PositiveOrZero Long version,
        @Size(max = 1000)
                @Pattern(
                        regexp = "^[\\P{Cntrl}\\n\\r\\t]*$",
                        message = "must not contain control characters")
                String comment) {}
