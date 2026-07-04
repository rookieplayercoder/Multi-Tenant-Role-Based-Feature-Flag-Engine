package com.prateek.featureflag.flag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Mirrors exactly the parameters of the existing
 * {@code FeatureFlagService.updateDetails(id, name, description, updatedBy)} —
 * no new service method needed. {@code key} and {@code flagType} are not
 * editable here; {@code enabled} is handled by the separate enable/disable
 * endpoints, which reuse {@code toggle(...)}.
 */
public record UpdateFeatureFlagRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description
) {
}
