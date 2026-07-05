package com.prateek.featureflag.apikey.dto;

import com.prateek.featureflag.apikey.EnvironmentApiKey;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned only from the create endpoint, exactly once. {@code apiKey}
 * carries the raw, unhashed secret the caller must copy now — since only
 * {@link EnvironmentApiKey#getKeyHash()} is ever persisted (same principle
 * as password hashing, per that entity's own Javadoc), there is no way to
 * recover or re-display this value later. Every other read of this key
 * (list, get) returns {@link ApiKeyResponse} instead, which omits it.
 */
public record CreatedApiKeyResponse(UUID id, UUID environmentId, String name, String keyPrefix, String apiKey,
                                     Instant createdAt) {

    public static CreatedApiKeyResponse of(EnvironmentApiKey entity, String rawApiKey) {
        return new CreatedApiKeyResponse(
                entity.getId(), entity.getEnvironment().getId(), entity.getName(), entity.getKeyPrefix(), rawApiKey,
                entity.getCreatedAt());
    }
}
