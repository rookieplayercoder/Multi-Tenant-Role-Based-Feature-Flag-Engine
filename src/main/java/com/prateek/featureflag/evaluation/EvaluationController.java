package com.prateek.featureflag.evaluation;

import com.prateek.featureflag.environment.Environment;
import com.prateek.featureflag.flag.FeatureFlagService;
import com.prateek.featureflag.environment.EnvironmentService;
import com.prateek.featureflag.evaluation.dto.EvaluateFlagRequest;
import com.prateek.featureflag.evaluation.dto.EvaluateFlagResponse;
import com.prateek.featureflag.flag.FeatureFlag;
import com.prateek.featureflag.organization.MemberRole;
import com.prateek.featureflag.organization.OrganizationAuthorizationService;
import com.prateek.featureflag.security.CustomUserDetails;
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

import java.util.UUID;

/**
 * Dashboard-side flag evaluation — lets an authenticated org member test how
 * a flag resolves for a given user/attributes, scoped to one environment.
 * This is deliberately JWT-authenticated only; there is no API-key/SDK path
 * yet (that's a separate, later concern once {@code EnvironmentApiKey}
 * authentication is wired up).
 * <p>
 * Requires {@code OWNER}, {@code ADMIN}, or {@code EDITOR} in the
 * organization owning the environment, resolved via
 * {@code environment.getProject().getOrganization().getId()} — the same
 * two-hop lazy-association pattern already used by
 * {@code EnvironmentController} and {@code FeatureFlagController}.
 */
@RestController
@RequestMapping("/api/environments/{environmentId}/evaluate")
public class EvaluationController {

    private final FeatureFlagService featureFlagService;
    private final FeatureFlagEvaluationService featureFlagEvaluationService;
    private final EnvironmentService environmentService;
    private final OrganizationAuthorizationService organizationAuthorizationService;

    public EvaluationController(FeatureFlagService featureFlagService,
                                 FeatureFlagEvaluationService featureFlagEvaluationService,
                                 EnvironmentService environmentService,
                                 OrganizationAuthorizationService organizationAuthorizationService) {
        this.featureFlagService = featureFlagService;
        this.featureFlagEvaluationService = featureFlagEvaluationService;
        this.environmentService = environmentService;
        this.organizationAuthorizationService = organizationAuthorizationService;
    }

    @PostMapping
    public ResponseEntity<EvaluateFlagResponse> evaluate(@PathVariable UUID environmentId,
                                                           @Valid @RequestBody EvaluateFlagRequest request,
                                                           @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Environment environment = environmentService.getActiveById(environmentId);
            authorize(environment, principal);

            FeatureFlag flag = featureFlagService.getActiveByEnvironmentAndKey(environmentId, request.flagKey());

            RuleEvaluator.EvaluationContext context =
                    new RuleEvaluator.EvaluationContext(request.userIdentifier(), request.attributes());
            EvaluationResult result = featureFlagEvaluationService.evaluate(flag, context);

            return ResponseEntity.ok(EvaluateFlagResponse.from(result));
        } catch (EntityNotFoundException notFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private void authorize(Environment environment, CustomUserDetails principal) {
        UUID organizationId = environment.getProject().getOrganization().getId();
        organizationAuthorizationService.requireRole(
                organizationId, principal.getUser().getId(),
                MemberRole.OWNER, MemberRole.ADMIN, MemberRole.EDITOR);
    }
}
