package com.prateek.featureflag.segment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Segment}.
 * <p>
 * {@code findByOrganizationIdAndNameAndDeletedAtIsNull} mirrors
 * {@code uq_segments_org_name}. {@code findByOrganizationIdAndDeletedAtIsNull}
 * lists an org's active segments, backed by {@code idx_segments_organization_id}.
 */
public interface SegmentRepository extends JpaRepository<Segment, UUID> {

    Optional<Segment> findByOrganizationIdAndNameAndDeletedAtIsNull(UUID organizationId, String name);

    List<Segment> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId);
}
