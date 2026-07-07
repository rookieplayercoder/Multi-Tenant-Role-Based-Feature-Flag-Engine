package com.prateek.featureflag.metrics;

import com.prateek.featureflag.flag.FeatureFlag;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for {@link FlagEvaluationMetric}. Deliberately takes
 * primitive {@code value}/{@code reason} rather than an
 * {@code EvaluationResult} — keeps this package independent of the
 * {@code evaluation} package's result type, since {@code evaluation} in
 * turn depends on this service to record metrics; a two-way type
 * dependency between the packages would be an avoidable coupling smell.
 */
@Service
@Transactional(readOnly = true)
public class FlagEvaluationMetricService {

    private final FlagEvaluationMetricRepository flagEvaluationMetricRepository;

    public FlagEvaluationMetricService(FlagEvaluationMetricRepository flagEvaluationMetricRepository) {
        this.flagEvaluationMetricRepository = flagEvaluationMetricRepository;
    }

    /**
     * Records one evaluation outcome for "today" (server clock).
     * <p>
     * {@code REQUIRES_NEW} rather than the default {@code REQUIRED}:
     * {@code FeatureFlagEvaluationService} (the only caller) runs its
     * evaluation methods under a class-level {@code @Transactional(readOnly = true)}.
     * Joining that transaction under {@code REQUIRED} would inherit the
     * read-only flag and risk this write being silently dropped or
     * rejected depending on driver behavior. Forcing a new transaction
     * guarantees the write is independent of the caller's transaction
     * mode, and also means a metrics-write failure can never roll back or
     * block the evaluation result itself — the caller gets their answer
     * either way.
     * <p>
     * {@code flag.getEnvironment().getId()} is safe to call here even
     * though the caller's session may be suspended (a consequence of
     * {@code REQUIRES_NEW}): Hibernate's lazy proxy for an association
     * already knows its own identifier from the foreign key value alone —
     * calling {@code .getId()} on it does not trigger initialization or
     * require an open session, unlike calling any other accessor
     * (e.g. {@code .getName()}) would. Don't extend this method to read
     * further into {@code Environment} without re-checking that.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(FeatureFlag flag, boolean value, String reason) {
        flagEvaluationMetricRepository.incrementCount(
                flag.getId(), flag.getEnvironment().getId(), LocalDate.now(), value, reason);
    }

    /** Daily breakdown for a flag over [from, to] inclusive, ascending by date. */
    public List<FlagEvaluationMetric> getDailyBreakdown(UUID flagId, LocalDate from, LocalDate to) {
        return flagEvaluationMetricRepository
                .findByFeatureFlagIdAndEvaluationDateBetweenOrderByEvaluationDateAsc(flagId, from, to);
    }

    /** Rolls a daily breakdown up into totals — computed in Java, not a separate SQL aggregate query, since the row count here is always small (days x results x reasons). */
    public MetricsSummary summarize(List<FlagEvaluationMetric> dailyBreakdown) {
        long total = dailyBreakdown.stream().mapToLong(FlagEvaluationMetric::getCount).sum();
        long trueCount = dailyBreakdown.stream()
                .filter(FlagEvaluationMetric::isResult)
                .mapToLong(FlagEvaluationMetric::getCount)
                .sum();
        Map<String, Long> byReason = dailyBreakdown.stream()
                .collect(Collectors.groupingBy(FlagEvaluationMetric::getReason,
                        Collectors.summingLong(FlagEvaluationMetric::getCount)));
        return new MetricsSummary(total, trueCount, total - trueCount, byReason);
    }
}
