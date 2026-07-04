package com.prateek.featureflag.flag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link FlagVersion}.
 * <p>
 * {@code findByFeatureFlagIdOrderByVersionDesc} lists a flag's full version
 * history, newest first, backed by {@code idx_flag_versions_flag_id_version_desc}.
 * {@code findByFeatureFlagIdAndVersion} looks up one specific version and
 * mirrors {@code uq_flag_versions_flag_version}.
 * {@code findTopByFeatureFlagIdOrderByVersionDesc} is a direct shortcut for
 * "the latest snapshot of this flag" without paging through the full list.
 */
public interface FlagVersionRepository extends JpaRepository<FlagVersion, UUID> {

    List<FlagVersion> findByFeatureFlagIdOrderByVersionDesc(UUID featureFlagId);

    Optional<FlagVersion> findByFeatureFlagIdAndVersion(UUID featureFlagId, Integer version);

    Optional<FlagVersion> findTopByFeatureFlagIdOrderByVersionDesc(UUID featureFlagId);
}
