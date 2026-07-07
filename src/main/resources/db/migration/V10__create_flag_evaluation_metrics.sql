-- ============================================================================
-- V3__create_flag_evaluation_metrics.sql
-- Pre-aggregated evaluation counters for Module 11 (Analytics & Metrics).
--
-- Deliberately NOT one row per evaluation: at real SDK traffic volume that
-- would be the fastest-growing, least-bounded table in the system. Instead,
-- one row per (flag, environment, day, result, reason), incremented via an
-- atomic upsert. Bounded size regardless of evaluation volume; supports
-- "evaluations over time," "true/false split," and "reason breakdown"
-- queries, but not per-request forensic detail (audit_logs remains the
-- place for that, for the subset of evaluations that have a User actor).
-- ============================================================================

CREATE TABLE flag_evaluation_metrics (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feature_flag_id  UUID NOT NULL REFERENCES feature_flags(id) ON DELETE CASCADE,
    environment_id   UUID NOT NULL REFERENCES environments(id) ON DELETE CASCADE,
    evaluation_date  DATE NOT NULL,
    result           BOOLEAN NOT NULL,
    reason           VARCHAR(30) NOT NULL,
    count            BIGINT NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_flag_evaluation_metrics UNIQUE (feature_flag_id, evaluation_date, result, reason),
    CONSTRAINT ck_flag_evaluation_metrics_count_non_negative CHECK (count >= 0)
);

-- Primary access pattern: "this flag's metrics over a date range".
CREATE INDEX idx_flag_evaluation_metrics_flag_date ON flag_evaluation_metrics(feature_flag_id, evaluation_date);

-- Secondary pattern: "this environment's metrics", independent of which flag.
CREATE INDEX idx_flag_evaluation_metrics_environment_id ON flag_evaluation_metrics(environment_id);

COMMENT ON TABLE flag_evaluation_metrics IS
    'Pre-aggregated evaluation counters, one row per (flag, environment, day, result, reason). Incremented via atomic upsert, not one row per evaluation.';
COMMENT ON COLUMN flag_evaluation_metrics.environment_id IS
    'Denormalized from feature_flag_id -> environment to avoid a join on every metrics query.';
