package com.prateek.featureflag.apikey.dto;

import com.prateek.featureflag.apikey.EnvironmentApiKey;

import java.time.Instant;
import java.util.UUID;


public record CreatedApiKeyResponse(UUID id, UUID environmentId, String name, String keyPrefix, String apiKey,
                                     Instant createdAt) {

    public static CreatedApiKeyResponse of(EnvironmentApiKey entity, String rawApiKey) {
        return new CreatedApiKeyResponse(
                entity.getId(), entity.getEnvironment().getId(), entity.getName(), entity.getKeyPrefix(), rawApiKey,
                entity.getCreatedAt());
    }
}
