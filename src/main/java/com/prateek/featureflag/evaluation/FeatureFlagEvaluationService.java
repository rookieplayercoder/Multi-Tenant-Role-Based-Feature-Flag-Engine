package com.prateek.featureflag.evaluation;

import com.prateek.featureflag.flag.FeatureFlag;
import com.prateek.featureflag.flag.FlagType;
import com.prateek.featureflag.rules.FeatureRule;
import com.prateek.featureflag.rules.FeatureRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
 */
@Service
@Transactional(readOnly = true)
public class FeatureFlagEvaluationService {

    private final FeatureRuleService featureRuleService;
    private final RuleEvaluator ruleEvaluator;

    public FeatureFlagEvaluationService(FeatureRuleService featureRuleService, RuleEvaluator ruleEvaluator) {
        this.featureRuleService = featureRuleService;
        this.ruleEvaluator = ruleEvaluator;
    }

    public EvaluationResult evaluate(FeatureFlag flag, RuleEvaluator.EvaluationContext context) {
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
}
