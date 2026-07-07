package com.prateek.featureflag.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Rename only, matching {@code UpdateProjectRequest}/
 * {@code UpdateOrganizationRequest} — {@code key} (the environment's
 * dev/test/staging/prod slot) is part of its identity within the project
 * and isn't something a "rename" changes; {@code EnvironmentService} has
 * no {@code updateKey} method to bundle in even if it wanted to.
 */
public record UpdateEnvironmentRequest(
        @NotBlank @Size(max = 100) String name
) {
}
