package com.prateek.featureflag.metrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link FlagEvaluationMetric}.
 * <p>
 * {@link #incrementCount} is the one deliberate exception to this project's
 * "no {@code @Query} unless required" convention: a derived-query
 * read-then-write (find row, add 1, save) would race under concurrent
 * evaluations of the same flag on the same day — two concurrent writers
 * could both read {@code count=5} and both write {@code count=6}, silently
 * losing an increment. A native {@code INSERT ... ON CONFLICT ... DO UPDATE}
 * is atomic at the database level and can't lose an update that way.
 */
public interface FlagEvaluationMetricRepository extends JpaRepository<FlagEvaluationMetric, UUID> {

    /**
     * Atomically creates the counter row for this (flag, environment, day,
     * result, reason) combination if it doesn't exist, or increments its
     * {@code count} by 1 if it does. Bypasses the JPA entity lifecycle
     * entirely — no entity is loaded, dirty-checked, or returned.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO flag_evaluation_metrics
                (id, feature_flag_id, environment_id, evaluation_date, result, reason, count, updated_at)
            VALUES
                (gen_random_uuid(), :flagId, :environmentId, :evaluationDate, :result, :reason, 1, now())
            ON CONFLICT (feature_flag_id, evaluation_date, result, reason)
            DO UPDATE SET count = flag_evaluation_metrics.count + 1, updated_at = now()
            """, nativeQuery = true)
    void incrementCount(@Param("flagId") UUID flagId,
                         @Param("environmentId") UUID environmentId,
                         @Param("evaluationDate") LocalDate evaluationDate,
                         @Param("result") boolean result,
                         @Param("reason") String reason);

    /** Mirrors {@code idx_flag_evaluation_metrics_flag_date} — the primary query pattern for a metrics view. */
    List<FlagEvaluationMetric> findByFeatureFlagIdAndEvaluationDateBetweenOrderByEvaluationDateAsc(
            UUID flagId, LocalDate from, LocalDate to);
}
