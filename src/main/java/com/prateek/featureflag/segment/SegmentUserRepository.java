package com.prateek.featureflag.segment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link SegmentUser}, keyed by the composite {@link SegmentUserId}.
 * <p>
 * {@code findBySegmentId} traverses the {@code segment} association
 * (resolves to {@code segment.id}) to list all members of a segment.
 * {@code findByIdUserIdentifier} reads the embedded-id field to answer
 * "which segments is this external user in", backed by
 * {@code idx_segment_users_user_identifier}.
 */
public interface SegmentUserRepository extends JpaRepository<SegmentUser, SegmentUserId> {

    List<SegmentUser> findBySegmentId(UUID segmentId);

    List<SegmentUser> findByIdUserIdentifier(String userIdentifier);
}
