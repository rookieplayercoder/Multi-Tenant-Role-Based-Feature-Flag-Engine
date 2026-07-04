package com.prateek.featureflag.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * {@code userIdentifier} is optional — {@link com.prateek.featureflag.evaluation.RuleEvaluator}
 * already fails closed on percentage/segment rules when it's absent, which
 * is the correct behavior for an anonymous evaluation. {@code attributes}
 * is optional and null-safe; passed straight through to
 * {@code RuleEvaluator.EvaluationContext}, which defensively copies it.
 */
public record EvaluateFlagRequest(
        @NotBlank @Size(max = 150) String flagKey,
        String userIdentifier,
        Map<String, Object> attributes
) {
}
