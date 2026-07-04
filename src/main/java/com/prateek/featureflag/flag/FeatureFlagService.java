package com.prateek.featureflag.flag;

import com.prateek.featureflag.environment.Environment;
import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for {@link FeatureFlag}. Deliberately does not touch
 * {@link FlagVersion} snapshotting or {@code version} incrementing —
 * that coordination belongs to a future workflow/orchestration layer, not
 * this entity's own service, to keep each service focused on its own
 * aggregate root.
 */
@Service
@Transactional(readOnly = true)
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;

    public FeatureFlagService(FeatureFlagRepository featureFlagRepository) {
        this.featureFlagRepository = featureFlagRepository;
    }

    @Transactional
    public FeatureFlag create(Environment environment, String key, String name, User createdBy) {
        if (featureFlagRepository.findByEnvironmentIdAndKeyAndDeletedAtIsNull(environment.getId(), key).isPresent()) {
            throw new IllegalStateException(
                    "Flag key already exists in this environment: " + key);
        }
        return featureFlagRepository.save(new FeatureFlag(environment, key, name, createdBy));
    }

    public FeatureFlag getActiveById(UUID id) {
        return featureFlagRepository.findById(id)
                .filter(flag -> !flag.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException("Feature flag not found: " + id));
    }

    /**
     * Resolves a flag by its (environment, key) pair — the lookup the
     * evaluation endpoint needs. This exposes
     * {@code findByEnvironmentIdAndKeyAndDeletedAtIsNull}, which already
     * existed on {@link FeatureFlagRepository} (mirroring
     * {@code uq_feature_flags_env_key}) but wasn't previously surfaced
     * through the service facade; no new query logic is introduced.
     */
    public FeatureFlag getActiveByEnvironmentAndKey(UUID environmentId, String key) {
        return featureFlagRepository.findByEnvironmentIdAndKeyAndDeletedAtIsNull(environmentId, key)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Feature flag not found: environment=" + environmentId + ", key=" + key));
    }

    public List<FeatureFlag> listActiveByEnvironment(UUID environmentId) {
        return featureFlagRepository.findByEnvironmentIdAndDeletedAtIsNull(environmentId);
    }

    public List<FeatureFlag> listEnabledByEnvironment(UUID environmentId) {
        return featureFlagRepository.findByEnvironmentIdAndEnabledTrueAndDeletedAtIsNull(environmentId);
    }

    @Transactional
    public FeatureFlag updateDetails(UUID id, String name, String description, User updatedBy) {
        FeatureFlag flag = getActiveById(id);
        flag.setName(name);
        flag.setDescription(description);
        flag.setUpdatedBy(updatedBy);
        return featureFlagRepository.save(flag);
    }

    @Transactional
    public FeatureFlag toggle(UUID id, boolean enabled, User updatedBy) {
        FeatureFlag flag = getActiveById(id);
        flag.setEnabled(enabled);
        flag.setUpdatedBy(updatedBy);
        return featureFlagRepository.save(flag);
    }

    @Transactional
    public void softDelete(UUID id) {
        FeatureFlag flag = getActiveById(id);
        flag.setDeletedAt(Instant.now());
        featureFlagRepository.save(flag);
    }
}
