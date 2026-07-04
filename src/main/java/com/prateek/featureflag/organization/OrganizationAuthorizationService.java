package com.prateek.featureflag.organization;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.UUID;

/**
 * Authorization checks for organization-scoped roles. Deliberately separate
 * from {@link MemberService} — this is a policy/guard concern (answering
 * "is this user allowed to do X in this org"), not a CRUD concern on the
 * {@link Member} entity itself. Depends only on the existing, unmodified
 * {@link MemberRepository}.
 * <p>
 * Throws {@link AccessDeniedException} rather than returning a boolean —
 * that exception is already handled end-to-end by Spring Security's
 * {@code ExceptionTranslationFilter} (wired into the filter chain via
 * {@code SecurityConfig}), which converts it to {@code 403 Forbidden}
 * automatically. No new exception-handling infrastructure is needed.
 */
@Service
@Transactional(readOnly = true)
public class OrganizationAuthorizationService {

    private final MemberRepository memberRepository;

    public OrganizationAuthorizationService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * Verifies the given user holds one of {@code allowedRoles} within the
     * given organization, returning their {@link Member} row on success.
     * Fails closed: a user with no membership row at all is denied the
     * same as one with an insufficient role.
     */
    public Member requireRole(UUID organizationId, UUID userId, MemberRole... allowedRoles) {
        Member member = memberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "User %s is not a member of organization %s".formatted(userId, organizationId)));

        if (Arrays.stream(allowedRoles).noneMatch(role -> role == member.getRole())) {
            throw new AccessDeniedException(
                    "Role %s is not permitted; requires one of %s".formatted(member.getRole(), Arrays.toString(allowedRoles)));
        }
        return member;
    }
}
