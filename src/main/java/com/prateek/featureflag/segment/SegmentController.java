package com.prateek.featureflag.segment;

import com.prateek.featureflag.organization.MemberRole;
import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.organization.OrganizationAuthorizationService;
import com.prateek.featureflag.organization.OrganizationService;
import com.prateek.featureflag.segment.dto.AddSegmentMemberRequest;
import com.prateek.featureflag.segment.dto.CreateSegmentRequest;
import com.prateek.featureflag.segment.dto.UpdateSegmentRequest;
import com.prateek.featureflag.security.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Dashboard-facing management of {@link Segment}s and their membership —
 * the missing piece that lets a human actually build the reusable,
 * named user groups that {@code RuleEvaluator}/{@code FeatureRuleService}
 * already know how to target via a {@code CONDITION} rule with
 * {@code attribute = "segment"} (see {@code RuleEvaluator.SEGMENT_ATTRIBUTE}).
 * Nothing else in the codebase previously called
 * {@link SegmentService#create}/{@link SegmentUserService#addMember}.
 * <p>
 * Two route groups, matching the {@code ProjectController}/
 * {@code FeatureFlagController} convention: organization-scoped
 * ({@code /api/organizations/{organizationId}/segments}) for create/list,
 * and segment-scoped ({@code /api/segments/{segmentId}}) for
 * get/update/delete/membership.
 * <p>
 * Create/rename/update-description/delete/add-member/remove-member require
 * {@code OWNER} or {@code ADMIN} — the same stricter tier
 * {@code ApiKeyController} uses for minting/revoking SDK credentials, since
 * a segment feeds directly into live targeting rules and is just as
 * sensitive to change unexpectedly. Reads (list/get/list-members) are open
 * to any org member, matching {@code ApiKeyController.list}.
 */
@RestController
public class SegmentController {

    private final SegmentService segmentService;
    private final SegmentUserService segmentUserService;
    private final OrganizationService organizationService;
    private final OrganizationAuthorizationService organizationAuthorizationService;

    public SegmentController(SegmentService segmentService,
                             SegmentUserService segmentUserService,
                             OrganizationService organizationService,
                             OrganizationAuthorizationService organizationAuthorizationService) {
        this.segmentService = segmentService;
        this.segmentUserService = segmentUserService;
        this.organizationService = organizationService;
        this.organizationAuthorizationService = organizationAuthorizationService;
    }

    @PostMapping("/api/organizations/{organizationId}/segments")
    public ResponseEntity<SegmentResponse> create(@PathVariable UUID organizationId,
                                                  @Valid @RequestBody CreateSegmentRequest request,
                                                  @AuthenticationPrincipal CustomUserDetails principal) {
        requireManageRole(organizationId, principal);

        try {
            Organization organization = organizationService.getActiveById(organizationId);
            Segment segment = segmentService.create(organization, request.name(), principal.getUser());

            if (request.description() != null && !request.description().isBlank()) {
                segment = segmentService.updateDescription(segment.getId(), request.description(), principal.getUser());
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(SegmentResponse.from(segment));
        } catch (EntityNotFoundException organizationNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException duplicateName) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/api/organizations/{organizationId}/segments")
    public ResponseEntity<List<SegmentResponse>> listByOrganization(@PathVariable UUID organizationId,
                                                                    @AuthenticationPrincipal CustomUserDetails principal) {
        requireMembership(organizationId, principal);

        List<SegmentResponse> segments = segmentService.listActiveByOrganization(organizationId).stream()
                .map(SegmentResponse::from)
                .toList();
        return ResponseEntity.ok(segments);
    }

    @GetMapping("/api/segments/{segmentId}")
    public ResponseEntity<SegmentResponse> getById(@PathVariable UUID segmentId,
                                                   @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Segment segment = segmentService.getActiveById(segmentId);
            requireMembership(segment.getOrganization().getId(), principal);
            return ResponseEntity.ok(SegmentResponse.from(segment));
        } catch (EntityNotFoundException segmentNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/api/segments/{segmentId}")
    public ResponseEntity<SegmentResponse> update(@PathVariable UUID segmentId,
                                                  @Valid @RequestBody UpdateSegmentRequest request,
                                                  @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Segment segment = segmentService.getActiveById(segmentId);
            requireManageRole(segment.getOrganization().getId(), principal);

            Segment renamed = segmentService.rename(segmentId, request.name(), principal.getUser());
            Segment updated = segmentService.updateDescription(segmentId, request.description(), principal.getUser());
            return ResponseEntity.ok(SegmentResponse.from(updated));
        } catch (EntityNotFoundException segmentNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException duplicateName) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @DeleteMapping("/api/segments/{segmentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID segmentId,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Segment segment = segmentService.getActiveById(segmentId);
            requireManageRole(segment.getOrganization().getId(), principal);

            segmentService.softDelete(segmentId, principal.getUser());
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException segmentNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/api/segments/{segmentId}/members")
    public ResponseEntity<SegmentMemberResponse> addMember(@PathVariable UUID segmentId,
                                                           @Valid @RequestBody AddSegmentMemberRequest request,
                                                           @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Segment segment = segmentService.getActiveById(segmentId);
            requireManageRole(segment.getOrganization().getId(), principal);

            SegmentUser member = segmentUserService.addMember(segment, request.userIdentifier(), principal.getUser());
            return ResponseEntity.status(HttpStatus.CREATED).body(SegmentMemberResponse.from(member));
        } catch (EntityNotFoundException segmentNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException alreadyMember) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/api/segments/{segmentId}/members")
    public ResponseEntity<List<SegmentMemberResponse>> listMembers(@PathVariable UUID segmentId,
                                                                   @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Segment segment = segmentService.getActiveById(segmentId);
            requireMembership(segment.getOrganization().getId(), principal);

            List<SegmentMemberResponse> members = segmentUserService.listMembers(segmentId).stream()
                    .map(SegmentMemberResponse::from)
                    .toList();
            return ResponseEntity.ok(members);
        } catch (EntityNotFoundException segmentNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @DeleteMapping("/api/segments/{segmentId}/members")
    public ResponseEntity<Void> removeMember(@PathVariable UUID segmentId,
                                             @RequestParam String userIdentifier,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Segment segment = segmentService.getActiveById(segmentId);
            requireManageRole(segment.getOrganization().getId(), principal);

            segmentUserService.removeMember(segment, userIdentifier, principal.getUser());
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException notFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
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

    public record SegmentResponse(UUID id, UUID organizationId, String name, String description,
                                  Instant createdAt, Instant updatedAt) {
        static SegmentResponse from(Segment segment) {
            return new SegmentResponse(
                    segment.getId(), segment.getOrganization().getId(), segment.getName(), segment.getDescription(),
                    segment.getCreatedAt(), segment.getUpdatedAt());
        }
    }

    public record SegmentMemberResponse(String userIdentifier, Instant addedAt) {
        static SegmentMemberResponse from(SegmentUser member) {
            return new SegmentMemberResponse(member.getUserIdentifier(), member.getAddedAt());
        }
    }
}