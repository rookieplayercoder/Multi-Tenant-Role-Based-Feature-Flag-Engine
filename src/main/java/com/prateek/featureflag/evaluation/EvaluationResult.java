package com.prateek.featureflag.evaluation;

import com.prateek.featureflag.flag.FeatureFlag;

import java.time.Instant;
import java.util.UUID;

/**
 * Final outcome of evaluating one {@link FeatureFlag} against one
 * {@link RuleEvaluator.EvaluationContext}. Deliberately just a boolean
 * {@code value} — this schema has no multivariate/variation concept, so
 * "on for this user" is the entire result.
 */
public record EvaluationResult(UUID flagId, String flagKey, boolean value, Reason reason, Instant evaluatedAt) {

    public static EvaluationResult of(FeatureFlag flag, boolean value, Reason reason) {
        return new EvaluationResult(flag.getId(), flag.getKey(), value, reason, Instant.now());
    }

    /** Why the value came out the way it did — useful for debugging targeting rules. */
    public enum Reason {
        FLAG_DISABLED,
        BOOLEAN_ENABLED,
        NO_RULES_DEFINED,
        RULE_MATCHED,
        NO_RULE_MATCHED
    }
}
