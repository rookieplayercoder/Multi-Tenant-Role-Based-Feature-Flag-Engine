package com.prateek.featureflag.audit;

/**
 * The kind of entity an {@link AuditLog} entry refers to (paired with
 * {@code entityId} to identify the specific row). Replaces the per-service
 * {@code private static final String ENTITY_TYPE = "..."} constants that
 * previously supplied this as free text.
 * <p>
 * Stored via {@code @Enumerated(EnumType.STRING)} on {@link AuditLog}, same
 * reasoning as {@link AuditAction}.
 */
public enum ResourceType {
    ORGANIZATION,
    PROJECT,
    ENVIRONMENT,
    FEATURE_FLAG,
    FEATURE_RULE,
    MEMBER,
    SEGMENT
}