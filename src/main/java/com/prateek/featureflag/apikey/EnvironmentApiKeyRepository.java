package com.prateek.featureflag.apikey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link EnvironmentApiKey}.
 * <p>
 * {@code findByKeyHashAndRevokedAtIsNull} is the SDK authentication hot
 * path, backed by {@code idx_environment_api_keys_active}.
 * {@code findByEnvironmentId} lists an environment's keys (dashboard view),
 * backed by {@code idx_environment_api_keys_environment_id}.
 */
public interface EnvironmentApiKeyRepository extends JpaRepository<EnvironmentApiKey, UUID> {

    Optional<EnvironmentApiKey> findByKeyHashAndRevokedAtIsNull(String keyHash);

    List<EnvironmentApiKey> findByEnvironmentId(UUID environmentId);
}
