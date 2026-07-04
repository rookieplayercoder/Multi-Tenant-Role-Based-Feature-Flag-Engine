package com.prateek.featureflag.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. {@code password} is capped at 72 characters —
 * BCrypt silently truncates input beyond 72 bytes, so anything longer
 * would be accepted here but quietly weakened at hashing time; rejecting
 * it up front is more honest than letting that happen invisibly.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 255) String fullName
) {
}
