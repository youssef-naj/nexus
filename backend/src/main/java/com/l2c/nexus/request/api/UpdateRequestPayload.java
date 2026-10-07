package com.l2c.nexus.request.api;

import com.l2c.nexus.request.domain.RequestCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The full representation plus the version the client saw; a mismatch means a concurrent change.
 */
public record UpdateRequestPayload(
        @NotBlank
                @Size(max = 150)
                @Pattern(
                        regexp = "^[^\\p{Cntrl}]*$",
                        message = "must not contain control characters")
                String title,
        @Size(max = 5000)
                @Pattern(
                        regexp = "^[\\P{Cntrl}\\n\\r\\t]*$",
                        message = "must not contain control characters")
                String description,
        @NotNull RequestCategory category,
        LocalDate dueDate,
        UUID departmentId,
        @NotNull @PositiveOrZero Long version) {}
