package com.prateek.featureflag.organization;

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
 */
@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional
    public Member invite(Organization organization, User user, MemberRole role) {
        if (memberRepository.findByOrganizationIdAndUserId(organization.getId(), user.getId()).isPresent()) {
            throw new IllegalStateException(
                    "User %s is already a member of organization %s".formatted(user.getId(), organization.getId()));
        }
        return memberRepository.save(new Member(organization, user, role));
    }

    @Transactional
    public Member acceptInvite(UUID memberId) {
        Member member = getById(memberId);
        member.setJoinedAt(Instant.now());
        return memberRepository.save(member);
    }

    @Transactional
    public Member changeRole(UUID memberId, MemberRole newRole) {
        Member member = getById(memberId);
        member.setRole(newRole);
        return memberRepository.save(member);
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
    public void remove(UUID memberId) {
        Member member = getById(memberId);
        memberRepository.delete(member);
    }
}
