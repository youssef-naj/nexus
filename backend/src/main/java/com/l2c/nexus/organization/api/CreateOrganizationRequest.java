package com.l2c.nexus.organization.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank
                @Size(max = 120)
                @Pattern(
                        regexp = "^[^\\p{Cntrl}]*$",
                        message = "must not contain control characters")
                String name) {}
