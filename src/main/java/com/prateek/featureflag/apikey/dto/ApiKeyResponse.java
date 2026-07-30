package com.prateek.featureflag.apikey.dto;

import com.prateek.featureflag.apikey.EnvironmentApiKey;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyResponse(UUID id, UUID environmentId, String name, String keyPrefix,
                              Instant createdAt, Instant revokedAt, Instant lastUsedAt) {

    public static ApiKeyResponse from(EnvironmentApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.getId(), apiKey.getEnvironment().getId(), apiKey.getName(), apiKey.getKeyPrefix(),
                apiKey.getCreatedAt(), apiKey.getRevokedAt(), apiKey.getLastUsedAt());
    }
}
