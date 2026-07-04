package com.prateek.featureflag.environment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Environment}.
 * <p>
 * {@code findByProjectIdAndKeyAndDeletedAtIsNull} mirrors
 * {@code uq_environments_project_key}. The {@code key} parameter is the
 * {@link EnvironmentType} enum — {@link EnvironmentTypeConverter} applies
 * automatically ({@code autoApply = true}), so the lowercase DB
 * representation is handled transparently.
 * {@code findByProjectIdAndDeletedAtIsNull} lists a project's active
 * environments, backed by {@code idx_environments_project_id}.
 */
public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {

    Optional<Environment> findByProjectIdAndKeyAndDeletedAtIsNull(UUID projectId, EnvironmentType key);

    List<Environment> findByProjectIdAndDeletedAtIsNull(UUID projectId);
}
