package com.prateek.featureflag.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Rename only — {@code OrganizationService} has no {@code updateSlug}
 * method (a slug is effectively a stable identifier once created), so
 * unlike {@code UpdateSegmentRequest} this doesn't bundle a second field.
 */
public record UpdateOrganizationRequest(
        @NotBlank @Size(max = 255) String name
) {
}
