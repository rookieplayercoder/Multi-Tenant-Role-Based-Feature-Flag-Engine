package com.prateek.featureflag.evaluation.dto;

import com.prateek.featureflag.evaluation.EvaluationResult;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire-format mirror of {@link EvaluationResult}, kept as a distinct type
 * rather than returning {@code EvaluationResult} directly — the HTTP
 * contract shouldn't be forced to change every time the internal
 * evaluation model does, even though the two shapes are identical today.
 */
public record EvaluateFlagResponse(UUID flagId, String flagKey, boolean value,
                                    EvaluationResult.Reason reason, Instant evaluatedAt) {

    public static EvaluateFlagResponse from(EvaluationResult result) {
        return new EvaluateFlagResponse(
                result.flagId(), result.flagKey(), result.value(), result.reason(), result.evaluatedAt());
    }
}
