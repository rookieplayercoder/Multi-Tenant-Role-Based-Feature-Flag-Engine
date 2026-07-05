package com.prateek.featureflag.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code name} is a human-friendly label (e.g. "CI pipeline", "Mobile app
 * prod") — purely for identifying the key in the dashboard later, has no
 * bearing on authentication itself.
 */
public record CreateApiKeyRequest(
        @NotBlank @Size(max = 255) String name
) {
}
