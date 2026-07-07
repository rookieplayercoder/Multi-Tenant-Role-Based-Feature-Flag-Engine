package com.prateek.featureflag.segment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Matches {@code ck_segments_name_not_blank}; {@code description} is
 * optional (nullable {@code TEXT} column, no length constraint at the DB
 * level, but bounded here to match {@link com.prateek.featureflag.segment.Segment}'s
 * own {@code @Size(max = 2000)}).
 */
public record CreateSegmentRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description
) {
}
