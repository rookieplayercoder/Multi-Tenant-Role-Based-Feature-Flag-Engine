package com.prateek.featureflag.organisation;

/**
 * Role a {@link com.prateek.featureflag.user.User} holds within a specific {@link Organisation}, via {@link Member}.
 * <p>
 * Values must stay in sync with the {@code ck_members_role} CHECK constraint
 * in V1__initial_schema.sql. Stored as STRING (not ORDINAL) so reordering
 * this enum never corrupts persisted data.
 */
public enum MemberRole {
    OWNER,
    ADMIN,
    EDITOR,
    VIEWER
}
