package com.l2c.nexus.department.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DepartmentRequest(
        @NotBlank
                @Size(max = 80)
                @Pattern(
                        regexp = "^[^\\p{Cntrl}]*$",
                        message = "must not contain control characters")
                String name,
        @Size(max = 500)
                @Pattern(
                        regexp = "^[\\P{Cntrl}\\n\\r\\t]*$",
                        message = "must not contain control characters")
                String description) {}
