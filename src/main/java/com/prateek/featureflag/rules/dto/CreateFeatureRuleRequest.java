package com.prateek.featureflag.rules.dto;

import com.prateek.featureflag.rules.LogicalOperator;
import com.prateek.featureflag.rules.RuleOperator;
import com.prateek.featureflag.rules.RuleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Creates either a GROUP or CONDITION rule under a flag, optionally nested
 * under an existing rule via {@code parentRuleId} ({@code null} = root node).
 * <p>
 * Only the fields relevant to {@code ruleType} are actually used —
 * {@code FeatureRuleService.addGroupRule}/{@code addConditionRule} each
 * accept just the parameters that apply to their own shape, so e.g. a
 * GROUP request's {@code attribute}/{@code operator}/{@code value} are
 * simply never read. The DB's {@code ck_feature_rules_group_shape}
 * constraint is the final backstop if a required field for the chosen
 * type is missing.
 */
public record CreateFeatureRuleRequest(
        @NotNull RuleType ruleType,
        UUID parentRuleId,
        LogicalOperator logicalOperator,
        @Size(max = 100) String attribute,
        RuleOperator operator,
        String value,
        @Min(0) @Max(100) Integer rolloutPercentage,
        @NotNull @Min(0) Integer position
) {
}
