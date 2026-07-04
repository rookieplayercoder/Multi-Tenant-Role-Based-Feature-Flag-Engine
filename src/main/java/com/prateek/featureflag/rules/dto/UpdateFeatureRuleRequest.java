package com.prateek.featureflag.rules.dto;

import com.prateek.featureflag.rules.LogicalOperator;
import com.prateek.featureflag.rules.RuleOperator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Edits a rule's own content — not its position (see
 * {@code PATCH /api/rules/{ruleId}/position}, which reuses
 * {@code FeatureRuleService.updatePosition}) and not its type or tree
 * placement, both immutable via this API this batch. Every field is
 * optional; the controller applies only the ones relevant to the rule's
 * existing {@code ruleType} and ignores the rest.
 */
public record UpdateFeatureRuleRequest(
        LogicalOperator logicalOperator,
        @Size(max = 100) String attribute,
        RuleOperator operator,
        String value,
        @Min(0) @Max(100) Integer rolloutPercentage
) {
}
