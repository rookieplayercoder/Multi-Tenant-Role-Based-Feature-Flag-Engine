package com.prateek.featureflag.audit;

/**
 * Every action recorded in {@code audit_logs}. Replaces the free-text
 * {@code String} action literals (e.g. {@code "organization.created"}) that
 * every service previously passed to {@link AuditLogService#record}.
 * <p>
 * Stored via {@code @Enumerated(EnumType.STRING)} on {@link AuditLog}, so
 * reordering constants here is safe — only the constant name itself is
 * persisted, and renaming a constant is the one change that would require
 * a data migration.
 */
public enum AuditAction {
    ORGANIZATION_CREATED,
    ORGANIZATION_RENAMED,
    ORGANIZATION_DELETED,

    PROJECT_CREATED,
    PROJECT_RENAMED,
    PROJECT_DELETED,

    ENVIRONMENT_CREATED,
    ENVIRONMENT_RENAMED,
    ENVIRONMENT_DELETED,

    FEATURE_FLAG_CREATED,
    FEATURE_FLAG_UPDATED,
    FEATURE_FLAG_ENABLED,
    FEATURE_FLAG_DISABLED,
    FEATURE_FLAG_TYPE_CHANGED,
    FEATURE_FLAG_DELETED,
    FEATURE_FLAG_EVALUATED,
    FEATURE_FLAG_ROLLED_BACK,

    FEATURE_RULE_CREATED,
    FEATURE_RULE_UPDATED,
    FEATURE_RULE_REORDERED,
    FEATURE_RULE_DELETED,

    MEMBER_INVITED,
    MEMBER_INVITE_ACCEPTED,
    MEMBER_ROLE_CHANGED,
    MEMBER_REMOVED
}