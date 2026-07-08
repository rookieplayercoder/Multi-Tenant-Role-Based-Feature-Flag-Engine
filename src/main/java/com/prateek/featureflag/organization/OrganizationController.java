package com.prateek.featureflag.organization;

import com.prateek.featureflag.organization.dto.CreateOrganizationRequest;
import com.prateek.featureflag.organization.dto.InviteMemberRequest;
import com.prateek.featureflag.organization.dto.UpdateOrganizationRequest;
import com.prateek.featureflag.security.CustomUserDetails;
import com.prateek.featureflag.user.User;
import com.prateek.featureflag.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Organization CRUD and membership invitation. Matches
 * {@code /api/organizations/**}, which falls under the "everything else
 * requires authentication" rule in {@code SecurityConfig}.
 * <p>
 * No local try/catch remains here (Module 13 cleanup) —
 * {@code EntityNotFoundException}, {@code IllegalStateException}, and
 * {@code AccessDeniedException} (from {@code requireRole}) all propagate
 * to {@code GlobalExceptionHandler} now, which is the single place that
 * maps them to 404/409/403.
 * <p>
 * {@code requireManageRole} (OWNER/ADMIN) and {@code requireMembership}
 * (all four roles) mirror the naming already established in
 * {@code SegmentController}, applied here for the first time to this
 * controller: create/rename/delete/invite need manage rights; get/read
 * only needs membership.
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
        Organization organization = organizationService.createWithOwner(
                request.name(), request.slug(), principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrganizationResponse.from(organization));
    }

    @GetMapping
    public ResponseEntity<List<OrganizationResponse>> getOrganizations(
            @AuthenticationPrincipal CustomUserDetails principal) {

        List<OrganizationResponse> organizations = organizationService
                .getOrganizationsForUser(principal.getUser())
                .stream()
                .map(OrganizationResponse::from)
                .toList();

        return ResponseEntity.ok(organizations);
    }

    @GetMapping("/{organizationId}")
    public ResponseEntity<OrganizationResponse> getById(@PathVariable UUID organizationId,
                                                        @AuthenticationPrincipal CustomUserDetails principal) {
        requireMembership(organizationId, principal);
        Organization organization = organizationService.getActiveById(organizationId);
        return ResponseEntity.ok(OrganizationResponse.from(organization));
    }

    @PutMapping("/{organizationId}")
    public ResponseEntity<OrganizationResponse> rename(@PathVariable UUID organizationId,
                                                       @Valid @RequestBody UpdateOrganizationRequest request,
                                                       @AuthenticationPrincipal CustomUserDetails principal) {
        requireManageRole(organizationId, principal);
        Organization renamed = organizationService.rename(organizationId, request.name(), principal.getUser());
        return ResponseEntity.ok(OrganizationResponse.from(renamed));
    }

    @DeleteMapping("/{organizationId}")
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        requireManageRole(organizationId, principal);
        organizationService.softDelete(organizationId, principal.getUser());
        return ResponseEntity.noContent().build();
    }

    /**
     * Invites an existing registered user (by email) into the organization.
     * Requires the caller to be {@code OWNER} or {@code ADMIN}.
     */
    @PostMapping("/{organizationId}/members")
    public ResponseEntity<MemberResponse> inviteMember(@PathVariable UUID organizationId,
                                                       @Valid @RequestBody InviteMemberRequest request,
                                                       @AuthenticationPrincipal CustomUserDetails principal) {
        requireManageRole(organizationId, principal);

        Organization organization = organizationService.getActiveById(organizationId);
        User invitee = userService.getActiveByEmail(request.email());
        Member member = memberService.invite(organization, invitee, request.role(), principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(MemberResponse.from(member));
    }

    private void requireManageRole(UUID organizationId, CustomUserDetails principal) {
        organizationAuthorizationService.requireRole(
                organizationId, principal.getUser().getId(), MemberRole.OWNER, MemberRole.ADMIN);
    }

    private void requireMembership(UUID organizationId, CustomUserDetails principal) {
        organizationAuthorizationService.requireRole(
                organizationId, principal.getUser().getId(),
                MemberRole.OWNER, MemberRole.ADMIN, MemberRole.EDITOR, MemberRole.VIEWER);
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