package com.prateek.featureflag.segment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code userIdentifier} is an external end-user ID from the client's own
 * system (see {@link com.prateek.featureflag.segment.SegmentUser}'s own
 * Javadoc) — not validated against any internal table, just non-blank and
 * bounded to match the {@code VARCHAR(255)} column.
 */
public record AddSegmentMemberRequest(
        @NotBlank @Size(max = 255) String userIdentifier
) {
}
