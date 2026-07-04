package com.prateek.featureflag.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Organization}.
 * <p>
 * {@code findBySlugAndDeletedAtIsNull} / {@code existsBySlugAndDeletedAtIsNull}
 * mirror {@code uq_organizations_slug}, the partial unique index that only
 * enforces uniqueness among non-deleted rows (a soft-deleted org's slug is
 * free to be reused).
 */
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlugAndDeletedAtIsNull(String slug);
}
