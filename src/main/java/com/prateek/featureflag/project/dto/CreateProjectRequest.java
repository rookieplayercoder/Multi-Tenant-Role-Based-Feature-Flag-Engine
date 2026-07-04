package com.prateek.featureflag.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Matches the {@code ck_projects_key_format} CHECK constraint (lowercase
 * alphanumeric + hyphens), same {@code @Pattern} used on the
 * {@link com.prateek.featureflag.project.Project} entity itself. Uniqueness
 * of {@code key} within the organization is enforced by
 * {@code ProjectService.create}, not here.
 */
public record CreateProjectRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z0-9-]+$",
                message = "key must be lowercase alphanumeric with hyphens only") String key
) {
}
