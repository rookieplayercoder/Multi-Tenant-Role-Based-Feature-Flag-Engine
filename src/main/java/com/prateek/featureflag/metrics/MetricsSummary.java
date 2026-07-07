package com.prateek.featureflag.metrics;

import java.util.Map;

/** Aggregated view over a {@link FlagEvaluationMetric} daily breakdown for a date range. */
public record MetricsSummary(long total, long trueCount, long falseCount, Map<String, Long> byReason) {
}
