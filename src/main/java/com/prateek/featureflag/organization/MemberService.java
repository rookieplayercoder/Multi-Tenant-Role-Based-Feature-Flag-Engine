package com.prateek.featureflag.organization;

import com.prateek.featureflag.audit.AuditLogService;
import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for {@link Member}. Membership has no soft delete (see the
 * entity's own Javadoc) — removal here is a real, final {@code delete},
 * not a flag flip.
 * <p>
 * State-changing operations are recorded via the existing
 * {@link AuditLogService}. {@code OrganizationController.inviteMember} calls
 * the original 3-arg {@code invite(Organization, User, MemberRole)}, which
 * has no separate actor (inviter) to attribute a log entry to, so it's left
 * unchanged (unlogged) to avoid touching {@code OrganizationController}. A
 * new 4-arg {@code invite(..., User actor)} overload is added alongside it
 * that performs the same invite and also logs "member invited".
 * {@code acceptInvite}, {@code changeRole}, and {@code remove} had no
 * external callers at all, so an {@code actor} parameter was added directly
 * to each, matching the pattern used for {@code OrganizationService}/
 * {@code ProjectService}/{@code EnvironmentService}.
 */
@Service
@Transactional(readOnly = true)
public class MemberService {

    private static final String ENTITY_TYPE = "Member";

    private final MemberRepository memberRepository;
    private final AuditLogService auditLogService;

    public MemberService(MemberRepository memberRepository, AuditLogService auditLogService) {
        this.memberRepository = memberRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Member invite(Organization organization, User user, MemberRole role) {
        if (memberRepository.findByOrganizationIdAndUserId(organization.getId(), user.getId()).isPresent()) {
            throw new IllegalStateException(
                    "User %s is already a member of organization %s".formatted(user.getId(), organization.getId()));
        }
        return memberRepository.save(new Member(organization, user, role));
    }

    /**
     * Same invite logic as {@link #invite(Organization, User, MemberRole)},
     * plus an audit log entry attributed to {@code actor} (the inviter).
     */
    @Transactional
    public Member invite(Organization organization, User user, MemberRole role, User actor) {
        Member member = invite(organization, user, role);
        auditLogService.record(organization, actor, "member.invited", ENTITY_TYPE, member.getId(), null);
        return member;
    }

    @Transactional
    public Member acceptInvite(UUID memberId, User actor) {
        Member member = getById(memberId);
        member.setJoinedAt(Instant.now());
        Member saved = memberRepository.save(member);
        auditLogService.record(saved.getOrganization(), actor, "member.invite_accepted", ENTITY_TYPE,
                saved.getId(), null);
        return saved;
    }

    @Transactional
    public Member changeRole(UUID memberId, MemberRole newRole, User actor) {
        Member member = getById(memberId);
        member.setRole(newRole);
        Member saved = memberRepository.save(member);
        auditLogService.record(saved.getOrganization(), actor, "member.role_changed", ENTITY_TYPE,
                saved.getId(), null);
        return saved;
    }

    public Member getById(UUID memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("Member not found: " + memberId));
    }

    public List<Member> listByOrganization(UUID organizationId) {
        return memberRepository.findByOrganizationId(organizationId);
    }

    public List<Member> listByUser(UUID userId) {
        return memberRepository.findByUserId(userId);
    }

    @Transactional
    public void remove(UUID memberId, User actor) {
        Member member = getById(memberId);
        Organization organization = member.getOrganization();
        memberRepository.delete(member);
        auditLogService.record(organization, actor, "member.removed", ENTITY_TYPE, memberId, null);
    }
}