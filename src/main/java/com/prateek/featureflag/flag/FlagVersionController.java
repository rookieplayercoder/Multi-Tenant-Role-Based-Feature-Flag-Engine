package com.prateek.featureflag.flag;

import com.prateek.featureflag.organization.MemberRole;
import com.prateek.featureflag.organization.OrganizationAuthorizationService;
import com.prateek.featureflag.security.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read access to a {@link FeatureFlag}'s version history — Module 10,
 * batch 1. Rollback (batch 2) isn't here yet.
 * <p>
 * Nests under {@code /api/flags/{flagId}/versions}, matching how
 * {@link FeatureFlagController} scopes its flag-level endpoints under
 * {@code /api/flags/{flagId}}. Kept as its own controller rather than
 * added to {@link FeatureFlagController} — that class's Javadoc treats its
 * six operations as a fixed, deliberately-scoped set, and versioning is a
 * distinct concern (append-only history vs. current-state CRUD), the same
 * reasoning that already put {@code AuditLogController} in its own class
 * rather than folded into {@code OrganizationController}.
 * <p>
 * Same role gate as {@link FeatureFlagController}: {@code OWNER}/{@code ADMIN}/
 * {@code EDITOR}, no {@code VIEWER} access — versioning is scoped to
 * whoever can already see/change the flag itself, not opened any wider.
 */
@RestController
public class FlagVersionController {

    private final FeatureFlagService featureFlagService;
    private final FlagVersionService flagVersionService;
    private final OrganizationAuthorizationService organizationAuthorizationService;

    public FlagVersionController(FeatureFlagService featureFlagService,
                                 FlagVersionService flagVersionService,
                                 OrganizationAuthorizationService organizationAuthorizationService) {
        this.featureFlagService = featureFlagService;
        this.flagVersionService = flagVersionService;
        this.organizationAuthorizationService = organizationAuthorizationService;
    }

    /** Full version history for a flag, newest first. */
    @GetMapping("/api/flags/{flagId}/versions")
    public ResponseEntity<List<FlagVersionResponse>> history(@PathVariable UUID flagId,
                                                             @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            FeatureFlag flag = featureFlagService.getActiveById(flagId);
            authorize(flag.getEnvironment().getProject().getOrganization().getId(), principal);

            List<FlagVersionResponse> versions = flagVersionService.listHistory(flagId).stream()
                    .map(FlagVersionResponse::from)
                    .toList();
            return ResponseEntity.ok(versions);
        } catch (EntityNotFoundException flagNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /** One specific version's snapshot. 404 if the flag doesn't exist, or if it exists but has no such version. */
    @GetMapping("/api/flags/{flagId}/versions/{version}")
    public ResponseEntity<FlagVersionResponse> getVersion(@PathVariable UUID flagId, @PathVariable Integer version,
                                                          @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            FeatureFlag flag = featureFlagService.getActiveById(flagId);
            authorize(flag.getEnvironment().getProject().getOrganization().getId(), principal);

            FlagVersion flagVersion = flagVersionService.getVersion(flagId, version);
            return ResponseEntity.ok(FlagVersionResponse.from(flagVersion));
        } catch (EntityNotFoundException notFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Restores the flag to a prior version's state. Returns the flag's
     * current-state representation (same shape {@link FeatureFlagController}
     * uses), not a {@link FlagVersionResponse} — the caller asked "what does
     * the flag look like now", and that's a {@code FeatureFlagResponse}
     * question, even though the mechanism underneath appends a new
     * {@code FlagVersion} row.
     */
    @PostMapping("/api/flags/{flagId}/versions/{version}/rollback")
    public ResponseEntity<FeatureFlagController.FeatureFlagResponse> rollback(
            @PathVariable UUID flagId, @PathVariable Integer version,
            @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            FeatureFlag flag = featureFlagService.getActiveById(flagId);
            authorize(flag.getEnvironment().getProject().getOrganization().getId(), principal);

            FeatureFlag rolledBack = featureFlagService.rollback(flagId, version, principal.getUser());
            return ResponseEntity.ok(FeatureFlagController.FeatureFlagResponse.from(rolledBack));
        } catch (EntityNotFoundException notFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /** Shared role gate — see class Javadoc. */
    private void authorize(UUID organizationId, CustomUserDetails principal) {
        organizationAuthorizationService.requireRole(
                organizationId, principal.getUser().getId(),
                MemberRole.OWNER, MemberRole.ADMIN, MemberRole.EDITOR);
    }

    /**
     * Wire-format mirror of {@link FlagVersion}, following the same nested-record
     * convention {@link FeatureFlagController} already uses for its own response
     * shape, rather than introducing a standalone {@code dto} file for this one
     * controller. {@code snapshot} is passed through as its raw JSON string (as
     * stored) rather than parsed back into a structure — this is a history
     * viewer, not something that re-interprets the payload.
     */
    public record FlagVersionResponse(UUID id, UUID featureFlagId, Integer version, String snapshot,
                                      UUID changedById, String changedByEmail, String changeSummary,
                                      Instant createdAt) {
        static FlagVersionResponse from(FlagVersion flagVersion) {
            return new FlagVersionResponse(
                    flagVersion.getId(),
                    flagVersion.getFeatureFlag().getId(),
                    flagVersion.getVersion(),
                    flagVersion.getSnapshot(),
                    flagVersion.getChangedBy().getId(),
                    flagVersion.getChangedBy().getEmail(),
                    flagVersion.getChangeSummary(),
                    flagVersion.getCreatedAt());
        }
    }
}