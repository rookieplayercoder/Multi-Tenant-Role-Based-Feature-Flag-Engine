package com.prateek.featureflag.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Member}.
 * <p>
 * {@code findByOrganizationIdAndUserId} mirrors {@code uq_members_org_user}
 * (a user's single membership row in a given org). {@code findByOrganizationId}
 * and {@code findByUserId} back {@code idx_members_organization_id} /
 * {@code idx_members_user_id} — listing an org's members and a user's
 * memberships across organizations, respectively.
 */
public interface MemberRepository extends JpaRepository<Member, UUID> {

    Optional<Member> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    List<Member> findByOrganizationId(UUID organizationId);

    List<Member> findByUserId(UUID userId);
}
