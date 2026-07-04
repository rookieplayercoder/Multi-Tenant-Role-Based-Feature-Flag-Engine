package com.prateek.featureflag.flag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link FeatureFlag}.
 * <p>
 * {@code findByEnvironmentIdAndKeyAndDeletedAtIsNull} mirrors
 * {@code uq_feature_flags_env_key}. {@code findByEnvironmentIdAndDeletedAtIsNull}
 * lists all active flags in an environment (dashboard view), backed by
 * {@code idx_feature_flags_environment_id}.
 * {@code findByEnvironmentIdAndEnabledTrueAndDeletedAtIsNull} backs the
 * hot-path {@code idx_feature_flags_env_enabled} index used by the
 * evaluation endpoint and cache warm-up.
 */
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    Optional<FeatureFlag> findByEnvironmentIdAndKeyAndDeletedAtIsNull(UUID environmentId, String key);

    List<FeatureFlag> findByEnvironmentIdAndDeletedAtIsNull(UUID environmentId);

    List<FeatureFlag> findByEnvironmentIdAndEnabledTrueAndDeletedAtIsNull(UUID environmentId);
}
