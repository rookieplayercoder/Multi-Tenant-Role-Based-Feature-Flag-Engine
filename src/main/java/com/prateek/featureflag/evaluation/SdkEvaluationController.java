package com.prateek.featureflag.evaluation;

import com.prateek.featureflag.environment.Environment;
import com.prateek.featureflag.evaluation.dto.EvaluateFlagRequest;
import com.prateek.featureflag.evaluation.dto.EvaluateFlagResponse;
import com.prateek.featureflag.flag.FeatureFlag;
import com.prateek.featureflag.flag.FeatureFlagService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SDK-facing flag evaluation, authenticated by {@code X-Api-Key} via
 * {@link com.prateek.featureflag.security.apikey.ApiKeyAuthenticationFilter}
 * — no JWT, no {@code environmentId} in the URL, and no
 * {@code OrganizationAuthorizationService} role check. The environment is
 * implied entirely by which API key authenticated the request; the key
 * itself is the authorization boundary for machine clients, since there's
 * no human role to check. Reuses {@link EvaluateFlagRequest}/
 * {@link EvaluateFlagResponse} from the dashboard evaluation endpoint
 * (Module 7 Batch 1) unmodified — same wire shape, different auth path.
 */
@RestController
@RequestMapping("/api/sdk")
public class SdkEvaluationController {

    private final FeatureFlagService featureFlagService;
    private final FeatureFlagEvaluationService featureFlagEvaluationService;

    public SdkEvaluationController(FeatureFlagService featureFlagService,
                                   FeatureFlagEvaluationService featureFlagEvaluationService) {
        this.featureFlagService = featureFlagService;
        this.featureFlagEvaluationService = featureFlagEvaluationService;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<EvaluateFlagResponse> evaluate(@AuthenticationPrincipal Environment environment,
                                                           @Valid @RequestBody EvaluateFlagRequest request) {
        try {
            FeatureFlag flag = featureFlagService.getActiveByEnvironmentAndKey(environment.getId(), request.flagKey());

            RuleEvaluator.EvaluationContext context =
                    new RuleEvaluator.EvaluationContext(request.userIdentifier(), request.attributes());
            EvaluationResult result = featureFlagEvaluationService.evaluate(flag, context);

            return ResponseEntity.ok(EvaluateFlagResponse.from(result));
        } catch (EntityNotFoundException flagNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
