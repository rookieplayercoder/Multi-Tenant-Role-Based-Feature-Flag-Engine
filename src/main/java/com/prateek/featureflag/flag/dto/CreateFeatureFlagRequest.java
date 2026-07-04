package com.prateek.featureflag.flag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code key} pattern matches {@code ck_feature_flags_key_format}. No
 * {@code flagType} field — {@code FeatureFlagService.create} has no way to
 * set it, and {@code PERCENTAGE}/{@code TARGETED} are meaningless without
 * the rule engine (explicitly out of scope this batch); new flags default
 * to {@code BOOLEAN} via the entity itself. {@code description} is
 * optional and applied via a follow-up {@code updateDetails} call in the
 * controller, not a new service method.
 */
public record CreateFeatureFlagRequest(
        @NotBlank @Size(max = 150) @Pattern(regexp = "^[a-z0-9-]+$",
                message = "key must be lowercase alphanumeric with hyphens only") String key,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description
) {
}
