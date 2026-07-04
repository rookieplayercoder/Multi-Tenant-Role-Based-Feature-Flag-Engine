package com.prateek.featureflag.flag;

import com.prateek.featureflag.environment.Environment;
import com.prateek.featureflag.environment.EnvironmentService;
import com.prateek.featureflag.flag.dto.CreateFeatureFlagRequest;
import com.prateek.featureflag.flag.dto.UpdateFeatureFlagRequest;
import com.prateek.featureflag.organization.MemberRole;
import com.prateek.featureflag.organization.OrganizationAuthorizationService;
import com.prateek.featureflag.security.CustomUserDetails;
import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * FeatureFlag CRUD plus enable/disable. Two route groups:
 * environment-scoped ({@code /api/environments/{environmentId}/flags}) for
 * create/list, and flag-scoped ({@code /api/flags/{flagId}}) for
 * get/update/enable/disable, matching how {@code EnvironmentController}
 * and {@code ProjectController} each nest under their parent.
 * <p>
 * All six operations require {@code OWNER}, {@code ADMIN}, or
 * {@code EDITOR} — per this batch's stated requirement, applied uniformly
 * to reads as well as writes, so {@code VIEWER} has no access to flags at
 * all here.
 * <p>
 * Flag-scoped endpoints resolve the owning organization via
 * {@code flag.getEnvironment().getProject().getOrganization().getId()} —
 * three chained LAZY loads, relying on this project's default
 * {@code spring.jpa.open-in-view=true}. This is noted as technical debt:
 * a purpose-built query would be more efficient, but adding one would mean
 * modifying a frozen repository without a compilation-forcing reason.
 */
@RestController
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;
    private final EnvironmentService environmentService;
    private final OrganizationAuthorizationService organizationAuthorizationService;

    public FeatureFlagController(FeatureFlagService featureFlagService,
                                  EnvironmentService environmentService,
                                  OrganizationAuthorizationService organizationAuthorizationService) {
        this.featureFlagService = featureFlagService;
        this.environmentService = environmentService;
        this.organizationAuthorizationService = organizationAuthorizationService;
    }

    @PostMapping("/api/environments/{environmentId}/flags")
    public ResponseEntity<FeatureFlagResponse> create(@PathVariable UUID environmentId,
                                                        @Valid @RequestBody CreateFeatureFlagRequest request,
                                                        @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Environment environment = environmentService.getActiveById(environmentId);
            authorize(environment.getProject().getOrganization().getId(), principal);

            User actor = principal.getUser();
            FeatureFlag flag = featureFlagService.create(environment, request.key(), request.name(), actor);

            if (request.description() != null && !request.description().isBlank()) {
                flag = featureFlagService.updateDetails(flag.getId(), flag.getName(), request.description(), actor);
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(FeatureFlagResponse.from(flag));
        } catch (EntityNotFoundException environmentNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException duplicateKey) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/api/environments/{environmentId}/flags")
    public ResponseEntity<List<FeatureFlagResponse>> listByEnvironment(@PathVariable UUID environmentId,
                                                                        @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Environment environment = environmentService.getActiveById(environmentId);
            authorize(environment.getProject().getOrganization().getId(), principal);

            List<FeatureFlagResponse> flags = featureFlagService.listActiveByEnvironment(environmentId).stream()
                    .map(FeatureFlagResponse::from)
                    .toList();
            return ResponseEntity.ok(flags);
        } catch (EntityNotFoundException environmentNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/api/flags/{flagId}")
    public ResponseEntity<FeatureFlagResponse> getById(@PathVariable UUID flagId,
                                                         @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            FeatureFlag flag = featureFlagService.getActiveById(flagId);
            authorize(flag.getEnvironment().getProject().getOrganization().getId(), principal);
            return ResponseEntity.ok(FeatureFlagResponse.from(flag));
        } catch (EntityNotFoundException flagNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/api/flags/{flagId}")
    public ResponseEntity<FeatureFlagResponse> update(@PathVariable UUID flagId,
                                                        @Valid @RequestBody UpdateFeatureFlagRequest request,
                                                        @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            FeatureFlag flag = featureFlagService.getActiveById(flagId);
            authorize(flag.getEnvironment().getProject().getOrganization().getId(), principal);

            FeatureFlag updated = featureFlagService.updateDetails(
                    flagId, request.name(), request.description(), principal.getUser());
            return ResponseEntity.ok(FeatureFlagResponse.from(updated));
        } catch (EntityNotFoundException flagNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/api/flags/{flagId}/enable")
    public ResponseEntity<FeatureFlagResponse> enable(@PathVariable UUID flagId,
                                                        @AuthenticationPrincipal CustomUserDetails principal) {
        return setEnabled(flagId, true, principal);
    }

    @PostMapping("/api/flags/{flagId}/disable")
    public ResponseEntity<FeatureFlagResponse> disable(@PathVariable UUID flagId,
                                                         @AuthenticationPrincipal CustomUserDetails principal) {
        return setEnabled(flagId, false, principal);
    }

    private ResponseEntity<FeatureFlagResponse> setEnabled(UUID flagId, boolean enabled, CustomUserDetails principal) {
        try {
            FeatureFlag flag = featureFlagService.getActiveById(flagId);
            authorize(flag.getEnvironment().getProject().getOrganization().getId(), principal);

            FeatureFlag toggled = featureFlagService.toggle(flagId, enabled, principal.getUser());
            return ResponseEntity.ok(FeatureFlagResponse.from(toggled));
        } catch (EntityNotFoundException flagNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /** Shared role gate for every operation in this controller — see class Javadoc. */
    private void authorize(UUID organizationId, CustomUserDetails principal) {
        organizationAuthorizationService.requireRole(
                organizationId, principal.getUser().getId(),
                MemberRole.OWNER, MemberRole.ADMIN, MemberRole.EDITOR);
    }

    public record FeatureFlagResponse(UUID id, UUID environmentId, String key, String name, String description,
                                       boolean enabled, FlagType flagType, Integer version,
                                       Instant createdAt, Instant updatedAt) {
        static FeatureFlagResponse from(FeatureFlag flag) {
            return new FeatureFlagResponse(
                    flag.getId(),
                    flag.getEnvironment().getId(),
                    flag.getKey(),
                    flag.getName(),
                    flag.getDescription(),
                    flag.isEnabled(),
                    flag.getFlagType(),
                    flag.getVersion(),
                    flag.getCreatedAt(),
                    flag.getUpdatedAt());
        }
    }
}
