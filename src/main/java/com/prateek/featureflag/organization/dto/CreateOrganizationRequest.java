package com.prateek.featureflag.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Matches the {@code ck_organizations_slug_format} CHECK constraint
 * (lowercase alphanumeric + hyphens) with the same {@code @Pattern} used on
 * the {@link com.prateek.featureflag.organization.Organization} entity
 * itself, so invalid input is rejected at the API boundary rather than
 * surfacing as a DB constraint violation.
 */
public record CreateOrganizationRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z0-9-]+$",
                message = "slug must be lowercase alphanumeric with hyphens only") String slug
) {
}
