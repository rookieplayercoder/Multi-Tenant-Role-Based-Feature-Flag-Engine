package com.prateek.featureflag.segment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Combined rename + description update, mirroring how
 * {@code UpdateFeatureFlagRequest} bundles both fields into one {@code PUT}
 * rather than exposing {@code SegmentService.rename}/{@code updateDescription}
 * as two separate endpoints.
 */
public record UpdateSegmentRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description
) {
}
