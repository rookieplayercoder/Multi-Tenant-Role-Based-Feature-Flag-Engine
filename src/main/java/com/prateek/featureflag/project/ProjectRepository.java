package com.prateek.featureflag.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Project}.
 * <p>
 * {@code findByOrganizationIdAndKeyAndDeletedAtIsNull} mirrors
 * {@code uq_projects_org_key}. {@code findByOrganizationIdAndDeletedAtIsNull}
 * lists an organization's active (non-deleted) projects, backed by
 * {@code idx_projects_organization_id}.
 */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByOrganizationIdAndKeyAndDeletedAtIsNull(UUID organizationId, String key);

    List<Project> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId);
}
