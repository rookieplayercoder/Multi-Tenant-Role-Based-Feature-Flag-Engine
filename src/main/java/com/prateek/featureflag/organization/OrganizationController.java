package com.prateek.featureflag.organization;

import com.prateek.featureflag.organization.dto.CreateOrganizationRequest;
import com.prateek.featureflag.organization.dto.InviteMemberRequest;
import com.prateek.featureflag.security.CustomUserDetails;
import com.prateek.featureflag.user.User;
import com.prateek.featureflag.user.UserService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Organization creation and membership invitation. Matches
 * {@code /api/organizations/**}, which falls under the "everything else
 * requires authentication" rule in {@code SecurityConfig} — the
 * authenticated principal is required for both endpoints below.
 * <p>
 * Response shapes are small nested records rather than exposing
 * {@link Organization}/{@link Member} entities directly (avoiding lazy-proxy
 * serialization issues and keeping the wire format independent of the
 * entity graph), scoped to this controller rather than added as new
 * standalone DTO files.
 */
@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final MemberService memberService;
    private final OrganizationAuthorizationService organizationAuthorizationService;
    private final UserService userService;

    public OrganizationController(OrganizationService organizationService,
                                   MemberService memberService,
                                   OrganizationAuthorizationService organizationAuthorizationService,
                                   UserService userService) {
        this.organizationService = organizationService;
        this.memberService = memberService;
        this.organizationAuthorizationService = organizationAuthorizationService;
        this.userService = userService;
    }

    /**
     * Creates an organization; the authenticated caller becomes its
     * {@code OWNER} atomically (see {@code OrganizationService.createWithOwner}).
     */
    @PostMapping
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request,
                                                         @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Organization organization = organizationService.createWithOwner(
                    request.name(), request.slug(), principal.getUser());
            return ResponseEntity.status(HttpStatus.CREATED).body(OrganizationResponse.from(organization));
        } catch (IllegalStateException duplicateSlug) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Invites an existing registered user (by email) into the organization.
     * Requires the caller to be {@code OWNER} or {@code ADMIN} in that
     * organization — enforced via {@link OrganizationAuthorizationService},
     * which throws {@code AccessDeniedException} (-> 403) on failure,
     * uncaught here so Spring Security's filter chain handles it.
     */
    @PostMapping("/{organizationId}/members")
    public ResponseEntity<MemberResponse> inviteMember(@PathVariable UUID organizationId,
                                                         @Valid @RequestBody InviteMemberRequest request,
                                                         @AuthenticationPrincipal CustomUserDetails principal) {
        organizationAuthorizationService.requireRole(
                organizationId, principal.getUser().getId(), MemberRole.OWNER, MemberRole.ADMIN);

        try {
            Organization organization = organizationService.getActiveById(organizationId);
            User invitee = userService.getActiveByEmail(request.email());
            Member member = memberService.invite(organization, invitee, request.role());
            return ResponseEntity.status(HttpStatus.CREATED).body(MemberResponse.from(member));
        } catch (EntityNotFoundException notFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException alreadyMember) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    public record OrganizationResponse(UUID id, String name, String slug) {
        static OrganizationResponse from(Organization organization) {
            return new OrganizationResponse(organization.getId(), organization.getName(), organization.getSlug());
        }
    }

    public record MemberResponse(UUID id, UUID userId, String email, MemberRole role,
                                  Instant invitedAt, Instant joinedAt) {
        static MemberResponse from(Member member) {
            return new MemberResponse(
                    member.getId(),
                    member.getUser().getId(),
                    member.getUser().getEmail(),
                    member.getRole(),
                    member.getInvitedAt(),
                    member.getJoinedAt());
        }
    }
}
