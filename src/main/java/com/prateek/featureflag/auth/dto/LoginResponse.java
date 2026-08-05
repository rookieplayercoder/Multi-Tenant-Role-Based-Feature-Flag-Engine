package com.prateek.featureflag.auth.dto;

import java.util.UUID;


public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        String email,
        String fullName
) {
}
