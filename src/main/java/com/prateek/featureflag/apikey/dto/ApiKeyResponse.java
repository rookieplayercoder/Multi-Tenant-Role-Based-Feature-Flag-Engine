package com.prateek.featureflag.apikey.dto;

import com.prateek.featureflag.apikey.EnvironmentApiKey;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe-to-display view of an {@link EnvironmentApiKey} — {@code keyPrefix}
 * only, never {@code keyHash} and never the raw secret (which isn't
 * persisted anywhere to begin with). Used for listing/reading existing
 * keys, as opposed to {@link CreatedApiKeyResponse}, which is the one-time
 * exception that includes the raw key at creation.
 */
public record ApiKeyResponse(UUID id, UUID environmentId, String name, String keyPrefix,
                              Instant createdAt, Instant revokedAt, Instant lastUsedAt) {

    public static ApiKeyResponse from(EnvironmentApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.getId(), apiKey.getEnvironment().getId(), apiKey.getName(), apiKey.getKeyPrefix(),
                apiKey.getCreatedAt(), apiKey.getRevokedAt(), apiKey.getLastUsedAt());
    }
}
