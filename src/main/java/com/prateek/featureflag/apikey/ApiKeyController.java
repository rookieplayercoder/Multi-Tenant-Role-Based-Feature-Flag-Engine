package com.prateek.featureflag.apikey;

import com.prateek.featureflag.apikey.dto.ApiKeyResponse;
import com.prateek.featureflag.apikey.dto.CreateApiKeyRequest;
import com.prateek.featureflag.apikey.dto.CreatedApiKeyResponse;
import com.prateek.featureflag.environment.Environment;
import com.prateek.featureflag.environment.EnvironmentService;
import com.prateek.featureflag.organization.MemberRole;
import com.prateek.featureflag.organization.OrganizationAuthorizationService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Dashboard-facing management of {@link EnvironmentApiKey}s — the missing
 * piece that lets a human actually mint the credential
 * {@code SdkEvaluationController}/{@code ApiKeyAuthenticationFilter}
 * consume. Nothing else in the codebase previously called
 * {@link EnvironmentApiKeyService#issue}.
 * <p>
 * Create and revoke require {@code OWNER}/{@code ADMIN} — a step stricter
 * than {@code FeatureFlagController}'s {@code OWNER}/{@code ADMIN}/{@code EDITOR},
 * since minting or killing a live SDK credential is a more sensitive action
 * than editing a flag. Listing only requires org membership at all (any
 * role), matching how {@code FeatureFlagController} treats reads — and
 * critically, {@link ApiKeyResponse} never carries the raw key or hash, so
 * a broader read audience here doesn't leak anything sensitive.
 */
@RestController
@RequestMapping("/api/environments/{environmentId}/api-keys")
public class ApiKeyController {

    private final EnvironmentApiKeyService environmentApiKeyService;
    private final EnvironmentService environmentService;
    private final OrganizationAuthorizationService organizationAuthorizationService;
    private final ApiKeyGenerator apiKeyGenerator;

    public ApiKeyController(EnvironmentApiKeyService environmentApiKeyService,
                             EnvironmentService environmentService,
                             OrganizationAuthorizationService organizationAuthorizationService,
                             ApiKeyGenerator apiKeyGenerator) {
        this.environmentApiKeyService = environmentApiKeyService;
        this.environmentService = environmentService;
        this.organizationAuthorizationService = organizationAuthorizationService;
        this.apiKeyGenerator = apiKeyGenerator;
    }

    @PostMapping
    public ResponseEntity<CreatedApiKeyResponse> create(@PathVariable UUID environmentId,
                                                          @Valid @RequestBody CreateApiKeyRequest request,
                                                          @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Environment environment = environmentService.getActiveById(environmentId);
            requireManageRole(environment, principal);

            ApiKeyGenerator.GeneratedKey generated = apiKeyGenerator.generate();
            EnvironmentApiKey issued = environmentApiKeyService.issue(
                    environment, generated.keyHash(), generated.keyPrefix(), request.name());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(CreatedApiKeyResponse.of(issued, generated.rawKey()));
        } catch (EntityNotFoundException environmentNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> list(@PathVariable UUID environmentId,
                                                       @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Environment environment = environmentService.getActiveById(environmentId);
            requireMembership(environment, principal);

            List<ApiKeyResponse> keys = environmentApiKeyService.listByEnvironment(environmentId).stream()
                    .map(ApiKeyResponse::from)
                    .toList();
            return ResponseEntity.ok(keys);
        } catch (EntityNotFoundException environmentNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @DeleteMapping("/{apiKeyId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID environmentId, @PathVariable UUID apiKeyId,
                                        @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Environment environment = environmentService.getActiveById(environmentId);
            requireManageRole(environment, principal);

            EnvironmentApiKey apiKey = environmentApiKeyService.getById(apiKeyId);
            if (!apiKey.getEnvironment().getId().equals(environmentId)) {
                // Belongs to a different environment (possibly a different org entirely) —
                // don't let this environment's OWNER/ADMIN role reach across that boundary.
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            environmentApiKeyService.revoke(apiKeyId);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException notFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private void requireManageRole(Environment environment, CustomUserDetails principal) {
        organizationAuthorizationService.requireRole(
                environment.getProject().getOrganization().getId(), principal.getUser().getId(),
                MemberRole.OWNER, MemberRole.ADMIN);
    }

    private void requireMembership(Environment environment, CustomUserDetails principal) {
        organizationAuthorizationService.requireRole(
                environment.getProject().getOrganization().getId(), principal.getUser().getId(),
                MemberRole.OWNER, MemberRole.ADMIN, MemberRole.EDITOR, MemberRole.VIEWER);
    }
}
