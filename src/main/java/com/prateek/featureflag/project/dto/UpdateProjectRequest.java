package com.prateek.featureflag.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Rename only — {@code ProjectService} has a separate {@code updateKey}
 * method that this deliberately doesn't bundle in, matching the same
 * choice {@code UpdateOrganizationRequest} made for slugs. A project's
 * {@code key} is part of its identity within the organization (referenced
 * elsewhere, e.g. in environment/flag lookups); changing it isn't a "rename".
 */
public record UpdateProjectRequest(
        @NotBlank @Size(max = 255) String name
) {
}
