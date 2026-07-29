package com.prateek.featureflag.apikey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnvironmentApiKeyRepository extends JpaRepository<EnvironmentApiKey, UUID> {

    Optional<EnvironmentApiKey> findByKeyHashAndRevokedAtIsNull(String keyHash);

    List<EnvironmentApiKey> findByEnvironmentId(UUID environmentId);
}
