package com.prateek.featureflag.environment.dto;

import com.prateek.featureflag.environment.EnvironmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code key} is the closed-set {@link EnvironmentType} enum, not a free
 * string — invalid values (anything other than dev/test/staging/prod) are
 * rejected by request deserialization itself, before validation even runs.
 * Uniqueness of {@code key} within the project is enforced by
 * {@code EnvironmentService.create}, not here.
 */
public record CreateEnvironmentRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull EnvironmentType key
) {
}
