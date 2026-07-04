package com.prateek.featureflag.flag;

import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service layer for {@link FlagVersion}. Append-only, matching the entity —
 * only a create ({@code recordSnapshot}) and read methods exist, no
 * update/delete, since {@link FlagVersion} has no setters beyond its
 * constructor.
 */
@Service
@Transactional(readOnly = true)
public class FlagVersionService {

    private final FlagVersionRepository flagVersionRepository;

    public FlagVersionService(FlagVersionRepository flagVersionRepository) {
        this.flagVersionRepository = flagVersionRepository;
    }

    @Transactional
    public FlagVersion recordSnapshot(FeatureFlag featureFlag, Integer version, String snapshot,
                                       User changedBy, String changeSummary) {
        if (flagVersionRepository.findByFeatureFlagIdAndVersion(featureFlag.getId(), version).isPresent()) {
            throw new IllegalStateException(
                    "Version %d already recorded for flag %s".formatted(version, featureFlag.getId()));
        }
        return flagVersionRepository.save(
                new FlagVersion(featureFlag, version, snapshot, changedBy, changeSummary));
    }

    public List<FlagVersion> listHistory(UUID featureFlagId) {
        return flagVersionRepository.findByFeatureFlagIdOrderByVersionDesc(featureFlagId);
    }

    public FlagVersion getVersion(UUID featureFlagId, Integer version) {
        return flagVersionRepository.findByFeatureFlagIdAndVersion(featureFlagId, version)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Version %d not found for flag %s".formatted(version, featureFlagId)));
    }

    public FlagVersion getLatest(UUID featureFlagId) {
        return flagVersionRepository.findTopByFeatureFlagIdOrderByVersionDesc(featureFlagId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No versions recorded yet for flag " + featureFlagId));
    }
}
