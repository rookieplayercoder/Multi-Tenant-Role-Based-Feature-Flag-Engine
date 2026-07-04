package com.prateek.featureflag.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login payload. No {@code @Email} format check here on purpose — an
 * unrecognized or malformed email should fail authentication the same
 * generic way a wrong password does, rather than leaking format-validity
 * information about which emails look "plausible".
 */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}
