-- ============================================================================
-- V1__initial_schema.sql
-- Multi-Tenant Role-Based Feature Flag Engine — Initial Schema
-- Target: PostgreSQL 18, Flyway-managed
-- ============================================================================
-- Conventions:
--   * All PKs are UUID, generated via gen_random_uuid() (pgcrypto extension).
--   * Timestamps are TIMESTAMPTZ — always store UTC-aware, never naive TIMESTAMP.
--   * Soft delete (deleted_at) is used on business entities whose deletion
--     must be reversible and whose historical FK references must stay valid:
--     organisations, users, projects, environments, feature_flags, segments.
--   * Soft-deleted rows are excluded from uniqueness via PARTIAL UNIQUE INDEXes
--     (WHERE deleted_at IS NULL), so a freed-up slug/key/email can be reused.
--   * flag_versions and audit_logs are append-only: no deleted_at, no UPDATE,
--     no DELETE — enforced at the application/service layer.
--   * FK actions follow one rule: CASCADE when the child is meaningless
--     without the parent; RESTRICT when the child is a historical/audit
--     record whose authorship must never silently disappear.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================================
-- Organisations — tenant root
-- ============================================================================
CREATE TABLE organisations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ NULL,

    CONSTRAINT ck_organisations_slug_format CHECK (slug ~ '^[a-z0-9-]+$'),
    CONSTRAINT ck_organisations_name_not_blank CHECK (btrim(name) <> '')
);

-- Partial unique index: slug must be unique only among non-deleted orgs,
-- so a deleted org's slug can be reused by a future organization.
CREATE UNIQUE INDEX uq_organisations_slug ON organisations(slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_organisations_deleted_at ON organisations(deleted_at) WHERE deleted_at IS NOT NULL;

COMMENT ON TABLE organisations IS 'Tenant root entity. Every tenant-owned row traces back here. Soft-deleted via deleted_at.';

-- ============================================================================
-- USERS — global identity, not tenant-scoped
-- ============================================================================
CREATE TABLE users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    full_name      VARCHAR(255) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ NULL,

    CONSTRAINT ck_users_email_format CHECK (email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    CONSTRAINT ck_users_full_name_not_blank CHECK (btrim(full_name) <> '')
);

CREATE UNIQUE INDEX uq_users_email ON users(email) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_deleted_at ON users(deleted_at) WHERE deleted_at IS NOT NULL;

COMMENT ON TABLE users IS 'Global login identity. A user may belong to multiple organisations via members. Soft-deleted via deleted_at.';

-- ============================================================================
-- MEMBERS — resolves users <-> organisations many-to-many, carries role
-- Not soft-deleted: membership removal is a discrete, final event. Re-invites
-- create a fresh row rather than resurrecting an old one.
-- ============================================================================
CREATE TABLE members (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organisations(id) ON DELETE CASCADE,
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role             VARCHAR(20) NOT NULL,
    invited_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    joined_at        TIMESTAMPTZ NULL,

    CONSTRAINT uq_members_org_user UNIQUE (organization_id, user_id),
    CONSTRAINT ck_members_role CHECK (role IN ('OWNER', 'ADMIN', 'EDITOR', 'VIEWER'))
);

CREATE INDEX idx_members_organization_id ON members(organization_id);
CREATE INDEX idx_members_user_id ON members(user_id);

COMMENT ON TABLE members IS 'Associative entity: a user''s role within a specific organization. CASCADE both FKs — a membership is meaningless without both sides.';

-- ============================================================================
-- PROJECTS — an organization owns many projects
-- ============================================================================
CREATE TABLE projects (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organisations(id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    key              VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ NULL,

    CONSTRAINT ck_projects_key_format CHECK (key ~ '^[a-z0-9-]+$')
);

CREATE INDEX idx_projects_organization_id ON projects(organization_id);
CREATE UNIQUE INDEX uq_projects_org_key ON projects(organization_id, key) WHERE deleted_at IS NULL;
CREATE INDEX idx_projects_deleted_at ON projects(deleted_at) WHERE deleted_at IS NOT NULL;

COMMENT ON TABLE projects IS 'CASCADE on organization_id — a project cannot exist outside its org. Soft-deleted via deleted_at.';

-- ============================================================================
-- ENVIRONMENTS — dev/test/staging/prod per project
-- ============================================================================
CREATE TABLE environments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    key          VARCHAR(50) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ NULL,

    CONSTRAINT ck_environments_key CHECK (key IN ('dev', 'test', 'staging', 'prod'))
);

CREATE INDEX idx_environments_project_id ON environments(project_id);
CREATE UNIQUE INDEX uq_environments_project_key ON environments(project_id, key) WHERE deleted_at IS NULL;
CREATE INDEX idx_environments_deleted_at ON environments(deleted_at) WHERE deleted_at IS NOT NULL;

COMMENT ON TABLE environments IS 'CASCADE on project_id. A flag''s state is scoped to environment, which is why feature_flags FKs here rather than to project_id directly.';

-- ============================================================================
-- FEATURE_FLAGS — one row = one flag's config in one environment
-- ============================================================================
CREATE TABLE feature_flags (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    environment_id   UUID NOT NULL REFERENCES environments(id) ON DELETE CASCADE,
    key              VARCHAR(150) NOT NULL,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    enabled          BOOLEAN NOT NULL DEFAULT false,
    flag_type        VARCHAR(20) NOT NULL DEFAULT 'BOOLEAN',
    version          INT NOT NULL DEFAULT 1,
    created_by       UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    updated_by       UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ NULL,

    CONSTRAINT ck_feature_flags_key_format CHECK (key ~ '^[a-z0-9-]+$'),
    CONSTRAINT ck_feature_flags_type CHECK (flag_type IN ('BOOLEAN', 'PERCENTAGE', 'TARGETED')),
    CONSTRAINT ck_feature_flags_version_positive CHECK (version >= 1)
);

CREATE INDEX idx_feature_flags_environment_id ON feature_flags(environment_id);
CREATE INDEX idx_feature_flags_created_by ON feature_flags(created_by);
CREATE INDEX idx_feature_flags_updated_by ON feature_flags(updated_by);
CREATE UNIQUE INDEX uq_feature_flags_env_key ON feature_flags(environment_id, key) WHERE deleted_at IS NULL;
CREATE INDEX idx_feature_flags_deleted_at ON feature_flags(deleted_at) WHERE deleted_at IS NOT NULL;
-- Hot-path lookup used by the evaluation endpoint and cache warm-up.
CREATE INDEX idx_feature_flags_env_enabled ON feature_flags(environment_id, enabled) WHERE deleted_at IS NULL;

COMMENT ON TABLE feature_flags IS 'Core entity. CASCADE on environment_id (flag cannot outlive its environment). RESTRICT on created_by/updated_by to preserve authorship even if a user is hard-deleted. Soft-deleted (archived) via deleted_at.';

-- ============================================================================
-- FEATURE_RULES — self-referencing tree, models the Composite pattern
-- Not soft-deleted: a rule's lifecycle is owned by the flag's version
-- history. When rules change, the prior shape is snapshotted into
-- flag_versions.snapshot and rule rows are recreated for the new version.
-- ============================================================================
CREATE TABLE feature_rules (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feature_flag_id      UUID NOT NULL REFERENCES feature_flags(id) ON DELETE CASCADE,
    parent_rule_id       UUID NULL REFERENCES feature_rules(id) ON DELETE CASCADE,
    rule_type            VARCHAR(20) NOT NULL,
    logical_operator     VARCHAR(10) NULL,
    attribute            VARCHAR(100) NULL,
    operator             VARCHAR(20) NULL,
    value                JSONB NULL,
    rollout_percentage   INT NULL,
    position             INT NOT NULL DEFAULT 0,

    CONSTRAINT ck_feature_rules_type CHECK (rule_type IN ('GROUP', 'CONDITION')),
    CONSTRAINT ck_feature_rules_logical_operator CHECK (logical_operator IS NULL OR logical_operator IN ('AND', 'OR', 'NOT')),
    CONSTRAINT ck_feature_rules_operator CHECK (operator IS NULL OR operator IN ('EQUALS', 'NOT_EQUALS', 'GREATER_THAN', 'LESS_THAN', 'IN', 'NOT_IN', 'CONTAINS')),
    CONSTRAINT ck_feature_rules_rollout_range CHECK (rollout_percentage IS NULL OR (rollout_percentage BETWEEN 0 AND 100)),
    -- A GROUP node carries a logical_operator and no leaf-only fields.
    -- A CONDITION node carries attribute/operator/value and no group-only fields.
    CONSTRAINT ck_feature_rules_group_shape CHECK (
        (rule_type = 'GROUP' AND logical_operator IS NOT NULL AND attribute IS NULL AND operator IS NULL AND value IS NULL)
        OR
        (rule_type = 'CONDITION' AND logical_operator IS NULL AND attribute IS NOT NULL AND operator IS NOT NULL)
    ),
    -- A rule cannot be its own parent (defends against a bad application-layer bug).
    CONSTRAINT ck_feature_rules_not_self_parent CHECK (id IS DISTINCT FROM parent_rule_id)
);

CREATE INDEX idx_feature_rules_feature_flag_id ON feature_rules(feature_flag_id);
CREATE INDEX idx_feature_rules_parent_rule_id ON feature_rules(parent_rule_id);
-- Speeds up "load full tree for this flag, ordered for deterministic rendering".
CREATE INDEX idx_feature_rules_flag_position ON feature_rules(feature_flag_id, position);
-- Supports queries filtering/aggregating on JSONB rule values (e.g. segment/attribute lookups).
CREATE INDEX idx_feature_rules_value_gin ON feature_rules USING GIN (value);

COMMENT ON TABLE feature_rules IS 'Self-referencing tree modeling the Composite pattern. CASCADE on both feature_flag_id and parent_rule_id — a rule (and its subtree) has no meaning without its owning flag/parent.';

-- ============================================================================
-- SEGMENTS — reusable named groups of end-user identifiers
-- ============================================================================
CREATE TABLE segments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organisations(id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ NULL,

    CONSTRAINT ck_segments_name_not_blank CHECK (btrim(name) <> '')
);

CREATE INDEX idx_segments_organization_id ON segments(organization_id);
CREATE UNIQUE INDEX uq_segments_org_name ON segments(organization_id, name) WHERE deleted_at IS NULL;
CREATE INDEX idx_segments_deleted_at ON segments(deleted_at) WHERE deleted_at IS NOT NULL;

COMMENT ON TABLE segments IS 'CASCADE on organization_id. Soft-deleted via deleted_at so historical rule references to a segment remain resolvable.';

-- ============================================================================
-- SEGMENT_USERS — membership of end users (client's own users) in a segment
-- Not soft-deleted: a pure associative row with no independent attributes;
-- removing membership is a final event.
-- ============================================================================
CREATE TABLE segment_users (
    segment_id       UUID NOT NULL REFERENCES segments(id) ON DELETE CASCADE,
    user_identifier  VARCHAR(255) NOT NULL,
    added_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (segment_id, user_identifier)
);

-- Supports "which segments is this external user in" lookups during evaluation.
CREATE INDEX idx_segment_users_user_identifier ON segment_users(user_identifier);

COMMENT ON TABLE segment_users IS 'user_identifier is an external end-user ID from the client system, NOT a FK to users.id. CASCADE on segment_id.';

-- ============================================================================
-- ENVIRONMENT_API_KEYS — SDK authentication, scoped to one environment
-- Domain-specific soft delete via revoked_at (no separate deleted_at needed).
-- ============================================================================
CREATE TABLE environment_api_keys (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    environment_id   UUID NOT NULL REFERENCES environments(id) ON DELETE CASCADE,
    key_hash         VARCHAR(255) NOT NULL,
    key_prefix       VARCHAR(20) NOT NULL,
    name             VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at       TIMESTAMPTZ NULL,
    last_used_at     TIMESTAMPTZ NULL,

    CONSTRAINT ck_environment_api_keys_name_not_blank CHECK (btrim(name) <> '')
);

CREATE INDEX idx_environment_api_keys_environment_id ON environment_api_keys(environment_id);
CREATE UNIQUE INDEX uq_environment_api_keys_key_hash ON environment_api_keys(key_hash);
-- Hot-path: SDK auth filters to non-revoked keys by hash.
CREATE INDEX idx_environment_api_keys_active ON environment_api_keys(key_hash) WHERE revoked_at IS NULL;

COMMENT ON TABLE environment_api_keys IS 'Only key_hash is stored, never the raw key. CASCADE on environment_id. revoked_at IS this table''s soft delete — a revoked key must remain visible in the dashboard for audit purposes.';

-- ============================================================================
-- FLAG_VERSIONS — immutable, append-only version history
-- No deleted_at: append-only tables are never deleted, only ever inserted.
-- ============================================================================
CREATE TABLE flag_versions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feature_flag_id   UUID NOT NULL REFERENCES feature_flags(id) ON DELETE CASCADE,
    version           INT NOT NULL,
    snapshot          JSONB NOT NULL,
    changed_by        UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    change_summary    VARCHAR(500),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_flag_versions_flag_version UNIQUE (feature_flag_id, version),
    CONSTRAINT ck_flag_versions_version_positive CHECK (version >= 1)
);

CREATE INDEX idx_flag_versions_feature_flag_id ON flag_versions(feature_flag_id);
CREATE INDEX idx_flag_versions_changed_by ON flag_versions(changed_by);
CREATE INDEX idx_flag_versions_flag_id_version_desc ON flag_versions(feature_flag_id, version DESC);
CREATE INDEX idx_flag_versions_snapshot_gin ON flag_versions USING GIN (snapshot);

COMMENT ON TABLE flag_versions IS 'Append-only, no deleted_at — enforced at the application/service layer (INSERT-only repository). CASCADE on feature_flag_id only matters for true hard-delete/GDPR purges; RESTRICT on changed_by preserves accountability.';

-- ============================================================================
-- AUDIT_LOGS — generic, append-only activity trail per organization
-- No deleted_at: append-only, immutable by design.
-- ============================================================================
CREATE TABLE audit_logs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organisations(id) ON DELETE CASCADE,
    actor_id         UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    action           VARCHAR(100) NOT NULL,
    entity_type      VARCHAR(50) NOT NULL,
    entity_id        UUID NOT NULL,
    metadata         JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_audit_logs_action_not_blank CHECK (btrim(action) <> ''),
    CONSTRAINT ck_audit_logs_entity_type_not_blank CHECK (btrim(entity_type) <> '')
);

CREATE INDEX idx_audit_logs_organization_id ON audit_logs(organization_id);
CREATE INDEX idx_audit_logs_actor_id ON audit_logs(actor_id);
-- Dominant access pattern: "recent activity for this org".
CREATE INDEX idx_audit_logs_org_created_at ON audit_logs(organization_id, created_at DESC);
-- Secondary access pattern: "history for this specific entity" (e.g. one flag's audit trail).
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_metadata_gin ON audit_logs USING GIN (metadata);

COMMENT ON TABLE audit_logs IS 'Append-only, no deleted_at. RESTRICT on actor_id — an audit trail must never lose its actor attribution. CASCADE on organization_id is standard tenant-owned-data cleanup.';

-- ============================================================================
-- END V1__initial_schema.sql
-- ============================================================================