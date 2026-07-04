package com.prateek.featureflag.rules;

import com.prateek.featureflag.flag.FeatureFlag;
import com.prateek.featureflag.flag.FeatureFlagService;
import com.prateek.featureflag.organization.MemberRole;
import com.prateek.featureflag.organization.OrganizationAuthorizationService;
import com.prateek.featureflag.rules.dto.CreateFeatureRuleRequest;
import com.prateek.featureflag.rules.dto.UpdateFeatureRuleRequest;
import com.prateek.featureflag.security.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for {@link FeatureRule}, including tree placement. Two route
 * groups, matching {@code FeatureFlagController}'s nesting convention:
 * flag-scoped ({@code /api/flags/{flagId}/rules}) for create/list-roots,
 * and rule-scoped ({@code /api/rules/{ruleId}}) for get/children/update/
 * reposition/delete.
 * <p>
 * All operations require {@code OWNER}, {@code ADMIN}, or {@code EDITOR}
 * in the owning organization, applied uniformly to reads and writes — same
 * policy as {@code FeatureFlagController}.
 * <p>
 * No evaluation engine is invoked here — this batch only manages the rule
 * tree's shape and content, never resolves it against a user context.
 * <p>
 * {@code PUT} is the one place this controller touches
 * {@link FeatureRuleRepository} directly instead of a service: the
 * existing {@link FeatureRuleService} exposes {@code updatePosition} but no
 * general field-update method, and the service is frozen this batch. That
 * method is explicitly {@code @Transactional} since, unlike every other
 * method here, it isn't already wrapped by a transactional service call.
 */
@RestController
public class FeatureRuleController {

    private final FeatureRuleService featureRuleService;
    private final FeatureRuleRepository featureRuleRepository;
    private final FeatureFlagService featureFlagService;
    private final OrganizationAuthorizationService organizationAuthorizationService;

    public FeatureRuleController(FeatureRuleService featureRuleService,
                                  FeatureRuleRepository featureRuleRepository,
                                  FeatureFlagService featureFlagService,
                                  OrganizationAuthorizationService organizationAuthorizationService) {
        this.featureRuleService = featureRuleService;
        this.featureRuleRepository = featureRuleRepository;
        this.featureFlagService = featureFlagService;
        this.organizationAuthorizationService = organizationAuthorizationService;
    }

    @PostMapping("/api/flags/{flagId}/rules")
    public ResponseEntity<FeatureRuleResponse> create(@PathVariable UUID flagId,
                                                       @Valid @RequestBody CreateFeatureRuleRequest request,
                                                       @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            FeatureFlag flag = featureFlagService.getActiveById(flagId);
            authorize(flag.getEnvironment().getProject().getOrganization().getId(), principal);

            FeatureRule parent = null;
            if (request.parentRuleId() != null) {
                parent = featureRuleRepository.findById(request.parentRuleId())
                        .orElseThrow(() -> new EntityNotFoundException("Parent rule not found: " + request.parentRuleId()));
                if (!parent.getFeatureFlag().getId().equals(flagId)) {
                    throw new IllegalArgumentException("Parent rule belongs to a different flag");
                }
            }

            FeatureRule created = switch (request.ruleType()) {
                case GROUP -> featureRuleService.addGroupRule(
                        flag, parent, request.logicalOperator(), request.position());
                case CONDITION -> featureRuleService.addConditionRule(
                        flag, parent, request.attribute(), request.operator(), request.value(),
                        request.rolloutPercentage(), request.position());
            };

            return ResponseEntity.status(HttpStatus.CREATED).body(FeatureRuleResponse.from(created));
        } catch (EntityNotFoundException notFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalArgumentException | DataIntegrityViolationException invalidShape) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/api/flags/{flagId}/rules")
    public ResponseEntity<List<FeatureRuleResponse>> listRootRules(@PathVariable UUID flagId,
                                                                    @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            FeatureFlag flag = featureFlagService.getActiveById(flagId);
            authorize(flag.getEnvironment().getProject().getOrganization().getId(), principal);

            List<FeatureRuleResponse> rules = featureRuleService.listRootRules(flagId).stream()
                    .map(FeatureRuleResponse::from)
                    .toList();
            return ResponseEntity.ok(rules);
        } catch (EntityNotFoundException flagNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/api/rules/{ruleId}")
    public ResponseEntity<FeatureRuleResponse> getById(@PathVariable UUID ruleId,
                                                        @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            FeatureRule rule = getRuleOrThrow(ruleId);
            authorize(organizationIdOf(rule), principal);
            return ResponseEntity.ok(FeatureRuleResponse.from(rule));
        } catch (EntityNotFoundException ruleNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/api/rules/{ruleId}/children")
    public ResponseEntity<List<FeatureRuleResponse>> listChildren(@PathVariable UUID ruleId,
                                                                   @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            FeatureRule rule = getRuleOrThrow(ruleId);
            authorize(organizationIdOf(rule), principal);

            List<FeatureRuleResponse> children = featureRuleService.listChildRules(ruleId).stream()
                    .map(FeatureRuleResponse::from)
                    .toList();
            return ResponseEntity.ok(children);
        } catch (EntityNotFoundException ruleNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/api/rules/{ruleId}")
    @Transactional
    public ResponseEntity<FeatureRuleResponse> update(@PathVariable UUID ruleId,
                                                       @Valid @RequestBody UpdateFeatureRuleRequest request,
                                                       @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            FeatureRule rule = getRuleOrThrow(ruleId);
            authorize(organizationIdOf(rule), principal);

            if (rule.getRuleType() == RuleType.GROUP) {
                if (request.logicalOperator() != null) {
                    rule.setLogicalOperator(request.logicalOperator());
                }
            } else {
                if (request.attribute() != null) {
                    rule.setAttribute(request.attribute());
                }
                if (request.operator() != null) {
                    rule.setOperator(request.operator());
                }
                if (request.value() != null) {
                    rule.setValue(request.value());
                }
                if (request.rolloutPercentage() != null) {
                    rule.setRolloutPercentage(request.rolloutPercentage());
                }
            }

            FeatureRule saved = featureRuleRepository.save(rule);
            return ResponseEntity.ok(FeatureRuleResponse.from(saved));
        } catch (EntityNotFoundException ruleNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (DataIntegrityViolationException invalidShape) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PatchMapping("/api/rules/{ruleId}/position")
    public ResponseEntity<FeatureRuleResponse> updatePosition(@PathVariable UUID ruleId,
                                                               @Valid @RequestBody PositionUpdateRequest request,
                                                               @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            FeatureRule rule = getRuleOrThrow(ruleId);
            authorize(organizationIdOf(rule), principal);

            FeatureRule updated = featureRuleService.updatePosition(ruleId, request.position());
            return ResponseEntity.ok(FeatureRuleResponse.from(updated));
        } catch (EntityNotFoundException ruleNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @DeleteMapping("/api/rules/{ruleId}")
    public ResponseEntity<Void> delete(@PathVariable UUID ruleId,
                                        @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            FeatureRule rule = getRuleOrThrow(ruleId);
            authorize(organizationIdOf(rule), principal);

            featureRuleService.delete(ruleId);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException ruleNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private FeatureRule getRuleOrThrow(UUID ruleId) {
        return featureRuleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("Feature rule not found: " + ruleId));
    }

    private UUID organizationIdOf(FeatureRule rule) {
        return rule.getFeatureFlag().getEnvironment().getProject().getOrganization().getId();
    }

    /** Shared role gate for every operation in this controller — see class Javadoc. */
    private void authorize(UUID organizationId, CustomUserDetails principal) {
        organizationAuthorizationService.requireRole(
                organizationId, principal.getUser().getId(),
                MemberRole.OWNER, MemberRole.ADMIN, MemberRole.EDITOR);
    }

    public record PositionUpdateRequest(@NotNull @Min(0) Integer position) {
    }

    public record FeatureRuleResponse(UUID id, UUID featureFlagId, UUID parentRuleId, RuleType ruleType,
                                       LogicalOperator logicalOperator, String attribute, RuleOperator operator,
                                       String value, Integer rolloutPercentage, Integer position) {
        static FeatureRuleResponse from(FeatureRule rule) {
            return new FeatureRuleResponse(
                    rule.getId(),
                    rule.getFeatureFlag().getId(),
                    rule.getParentRule() != null ? rule.getParentRule().getId() : null,
                    rule.getRuleType(),
                    rule.getLogicalOperator(),
                    rule.getAttribute(),
                    rule.getOperator(),
                    rule.getValue(),
                    rule.getRolloutPercentage(),
                    rule.getPosition());
        }
    }
}
