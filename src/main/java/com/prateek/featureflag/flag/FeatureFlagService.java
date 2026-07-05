package com.prateek.featureflag.flag;

import com.prateek.featureflag.environment.Environment;
import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service layer for {@link FeatureFlag}. After every state-changing
 * operation (create, updateDetails, toggle) it asks {@link FlagVersionService}
 * to append an immutable snapshot of the resulting flag state, keeping
 * {@code flag_versions} in lockstep with {@code FeatureFlag.version}. The
 * snapshotting itself — validation, persistence, uniqueness of
 * (flag, version) — remains entirely {@link FlagVersionService}'s
 * responsibility; this class only decides *when* to ask for it and *what*
 * the snapshot payload/version/summary should be.
 */
@Service
@Transactional(readOnly = true)
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final FlagVersionService flagVersionService;
    private final ObjectMapper objectMapper;

    public FeatureFlagService(FeatureFlagRepository featureFlagRepository,
                              FlagVersionService flagVersionService,
                              ObjectMapper objectMapper) {
        this.featureFlagRepository = featureFlagRepository;
        this.flagVersionService = flagVersionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FeatureFlag create(Environment environment, String key, String name, User createdBy) {
        if (featureFlagRepository.findByEnvironmentIdAndKeyAndDeletedAtIsNull(environment.getId(), key).isPresent()) {
            throw new IllegalStateException(
                    "Flag key already exists in this environment: " + key);
        }
        FeatureFlag flag = featureFlagRepository.save(new FeatureFlag(environment, key, name, createdBy));
        recordSnapshot(flag, createdBy, "Flag created");
        return flag;
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
        flag.setVersion(flag.getVersion() + 1);
        FeatureFlag saved = featureFlagRepository.save(flag);
        recordSnapshot(saved, updatedBy, "Flag details updated");
        return saved;
    }

    @Transactional
    public FeatureFlag toggle(UUID id, boolean enabled, User updatedBy) {
        FeatureFlag flag = getActiveById(id);
        flag.setEnabled(enabled);
        flag.setUpdatedBy(updatedBy);
        flag.setVersion(flag.getVersion() + 1);
        FeatureFlag saved = featureFlagRepository.save(flag);
        recordSnapshot(saved, updatedBy, enabled ? "Flag enabled" : "Flag disabled");
        return saved;
    }

    /**
     * Serializes the flag's current state and hands it to
     * {@link FlagVersionService#recordSnapshot} against the flag's own
     * {@code version} counter, so {@code flag_versions} always has a row
     * matching the current {@code FeatureFlag.version}.
     */
    private void recordSnapshot(FeatureFlag flag, User changedBy, String changeSummary) {
        try {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("id", flag.getId());
            state.put("environmentId", flag.getEnvironment().getId());
            state.put("key", flag.getKey());
            state.put("name", flag.getName());
            state.put("description", flag.getDescription());
            state.put("enabled", flag.isEnabled());
            state.put("flagType", flag.getFlagType().name());
            state.put("version", flag.getVersion());

            String snapshot = objectMapper.writeValueAsString(state);

            flagVersionService.recordSnapshot(
                    flag,
                    flag.getVersion(),
                    snapshot,
                    changedBy,
                    changeSummary
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize feature flag snapshot", e);
        }
    }

    @Transactional
    public void softDelete(UUID id) {
        FeatureFlag flag = getActiveById(id);
        flag.setDeletedAt(Instant.now());
        featureFlagRepository.save(flag);
    }
}