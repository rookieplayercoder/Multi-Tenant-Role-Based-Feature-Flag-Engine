package com.prateek.featureflag.evaluation;

import com.prateek.featureflag.audit.AuditLogService;
import com.prateek.featureflag.audit.AuditAction;
import com.prateek.featureflag.audit.ResourceType;
import com.prateek.featureflag.flag.FeatureFlag;
import com.prateek.featureflag.flag.FlagType;
import com.prateek.featureflag.metrics.FlagEvaluationMetricService;
import com.prateek.featureflag.rules.FeatureRule;
import com.prateek.featureflag.rules.FeatureRuleService;
import com.prateek.featureflag.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates a {@link FeatureFlag} for a single {@link RuleEvaluator.EvaluationContext}
 * and returns the final on/off result — no controller or SDK endpoint yet.
 * <p>
 * {@code BOOLEAN} flags never consult the rule tree: enabled means on, full
 * stop. {@code PERCENTAGE} and {@code TARGETED} both delegate to the same
 * rule-tree walk — the distinction between them is definitional (a
 * PERCENTAGE flag's rules typically lean on {@code rolloutPercentage}, a
 * TARGETED flag's on attribute/segment matching), not a different code
 * path, since a single {@code CONDITION} node already supports both
 * mechanisms independently (see {@code RuleEvaluator}).
 * <p>
 * Root-level rules are combined with an implicit OR: the flag is "on" for
 * this context if any one top-level rule matches, mirroring how each root
 * rule represents an independent targeting rule rather than a single
 * combined expression.
 * <p>
 * {@code @Transactional(readOnly = true)} at the class level matters here:
 * {@link RuleEvaluator} walks lazy associations
 * ({@code FeatureRule.childRules}, {@code FeatureRule.featureFlag}) that
 * must be read within the same open Hibernate session this method opens.
 * <p>
 * <b>Audit logging:</b> {@link #evaluate(FeatureFlag, RuleEvaluator.EvaluationContext, User)}
 * logs every evaluation via the existing {@link AuditLogService}, attributed
 * to the given {@code actor}. The original 2-arg {@link #evaluate(FeatureFlag, RuleEvaluator.EvaluationContext)}
 * is left unchanged and unlogged, and is what both existing callers
 * ({@code EvaluationController}, {@code SdkEvaluationController}) still use.
 * That split is deliberate, not partial compliance: {@code AuditLog.actor}
 * is a {@code NOT NULL User} — there is no "system"/anonymous user in this
 * schema. {@code EvaluationController} does have an authenticated
 * {@code User} it isn't yet passing through, so the 3-arg overload is ready
 * for it. {@code SdkEvaluationController} authenticates by API key against
 * an {@code Environment}, with no {@code User} at all in that path — logging
 * "every" evaluation there through this {@code User}-keyed audit trail would
 * mean fabricating an actor (e.g. the flag's last editor), which would
 * misattribute the log entry to someone who didn't request the evaluation.
 * Since this module only allows changes to this file, that gap can't be
 * closed here without either modifying {@code SdkEvaluationController} or
 * misrepresenting the audit trail, so it's called out rather than papered
 * over.
 * <p>
 * <b>Metrics (Module 11):</b> unlike audit logging, {@link FlagEvaluationMetricService}
 * needs no {@code User} at all, so it's recorded inside the 2-arg
 * {@link #evaluate(FeatureFlag, RuleEvaluator.EvaluationContext)} — the one
 * method both real callers actually invoke today — giving full coverage of
 * dashboard *and* SDK traffic from day one, unlike the audit trail above
 * which is structurally limited to the subset of evaluations with a human
 * actor.
 */
@Service
@Transactional(readOnly = true)
public class FeatureFlagEvaluationService {

    private static final ResourceType ENTITY_TYPE = ResourceType.FEATURE_FLAG;

    private final FeatureRuleService featureRuleService;
    private final RuleEvaluator ruleEvaluator;
    private final AuditLogService auditLogService;
    private final FlagEvaluationMetricService flagEvaluationMetricService;
    private final ObjectMapper objectMapper;

    public FeatureFlagEvaluationService(FeatureRuleService featureRuleService, RuleEvaluator ruleEvaluator,
                                        AuditLogService auditLogService,
                                        FlagEvaluationMetricService flagEvaluationMetricService,
                                        ObjectMapper objectMapper) {
        this.featureRuleService = featureRuleService;
        this.ruleEvaluator = ruleEvaluator;
        this.auditLogService = auditLogService;
        this.flagEvaluationMetricService = flagEvaluationMetricService;
        this.objectMapper = objectMapper;
    }

    public EvaluationResult evaluate(FeatureFlag flag, RuleEvaluator.EvaluationContext context) {
        EvaluationResult result = doEvaluate(flag, context);
        flagEvaluationMetricService.record(flag, result.value(), result.reason().name());
        return result;
    }

    private EvaluationResult doEvaluate(FeatureFlag flag, RuleEvaluator.EvaluationContext context) {
        if (!flag.isEnabled()) {
            return EvaluationResult.of(flag, false, EvaluationResult.Reason.FLAG_DISABLED);
        }

        if (flag.getFlagType() == FlagType.BOOLEAN) {
            return EvaluationResult.of(flag, true, EvaluationResult.Reason.BOOLEAN_ENABLED);
        }

        List<FeatureRule> rootRules = featureRuleService.listRootRules(flag.getId());
        if (rootRules.isEmpty()) {
            return EvaluationResult.of(flag, true, EvaluationResult.Reason.NO_RULES_DEFINED);
        }

        boolean matched = rootRules.stream().anyMatch(rule -> ruleEvaluator.evaluate(rule, context));
        return EvaluationResult.of(flag, matched,
                matched ? EvaluationResult.Reason.RULE_MATCHED : EvaluationResult.Reason.NO_RULE_MATCHED);
    }

    /**
     * Same evaluation logic as {@link #evaluate(FeatureFlag, RuleEvaluator.EvaluationContext)},
     * plus an audit log entry (via the existing {@link AuditLogService})
     * attributed to {@code actor}, capturing the result and reason as
     * metadata.
     */
    @Transactional
    public EvaluationResult evaluate(FeatureFlag flag, RuleEvaluator.EvaluationContext context, User actor) {
        EvaluationResult result = evaluate(flag, context);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("value", result.value());
        metadata.put("reason", result.reason().name());
        if (context.userIdentifier() != null) {
            metadata.put("userIdentifier", context.userIdentifier());
        }
        String metadataJson = objectMapper.writeValueAsString(metadata);

        auditLogService.record(
                flag.getEnvironment().getProject().getOrganization(), actor, AuditAction.FEATURE_FLAG_EVALUATED,
                ENTITY_TYPE, flag.getId(), metadataJson);

        return result;
    }
}