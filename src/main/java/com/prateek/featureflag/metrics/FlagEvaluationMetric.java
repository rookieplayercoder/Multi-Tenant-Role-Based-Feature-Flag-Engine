package com.prateek.featureflag.metrics;

import com.prateek.featureflag.environment.Environment;
import com.prateek.featureflag.flag.FeatureFlag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One pre-aggregated counter row for a {@link FeatureFlag}'s evaluations on
 * a single day, split by result and reason. Maps exactly to
 * {@code flag_evaluation_metrics} in V10__create_flag_evaluation_metrics.sql.
 * <p>
 * This entity is read-facing only. The actual increment happens via a
 * native {@code INSERT ... ON CONFLICT ... DO UPDATE} in
 * {@link FlagEvaluationMetricRepository#incrementCount}, which bypasses
 * JPA's entity lifecycle entirely (no {@code save()} call, no dirty
 * checking) — the standard approach for an atomic counter table under
 * concurrent writers, where a read-modify-write through the entity would
 * race.
 */
@Entity
@Table(
        name = "flag_evaluation_metrics",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_flag_evaluation_metrics",
                columnNames = {"feature_flag_id", "evaluation_date", "result", "reason"}
        )
)
public class FlagEvaluationMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feature_flag_id", nullable = false, updatable = false)
    private FeatureFlag featureFlag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false, updatable = false)
    private Environment environment;

    @Column(name = "evaluation_date", nullable = false, updatable = false)
    private LocalDate evaluationDate;

    @Column(name = "result", nullable = false, updatable = false)
    private boolean result;

    /** {@code EvaluationResult.Reason} name, stored as plain text rather than an enum FK-style link — see class Javadoc. */
    @Column(name = "reason", nullable = false, updatable = false, length = 30)
    private String reason;

    @Column(name = "count", nullable = false)
    private long count;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FlagEvaluationMetric() {
    }

    public UUID getId() {
        return id;
    }

    public FeatureFlag getFeatureFlag() {
        return featureFlag;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public LocalDate getEvaluationDate() {
        return evaluationDate;
    }

    public boolean isResult() {
        return result;
    }

    public String getReason() {
        return reason;
    }

    public long getCount() {
        return count;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FlagEvaluationMetric other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "FlagEvaluationMetric{date=%s, result=%s, reason='%s', count=%d}"
                .formatted(evaluationDate, result, reason, count);
    }
}
