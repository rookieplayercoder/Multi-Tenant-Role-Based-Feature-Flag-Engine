package com.prateek.featureflag.auth.dto;

import java.util.UUID;

/**
 * Returned by both {@code /register} and {@code /login} — registration
 * auto-issues a token so the client doesn't need a second round trip.
 * Never exposes {@code passwordHash}; only the fields a client actually
 * needs to attach the token to subsequent requests and render basic
 * identity.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        String email,
        String fullName
) {
}
