package com.prateek.featureflag.flag;

/**
 * The evaluation strategy a {@link FeatureFlag} uses. Must stay in sync with
 * the {@code ck_feature_flags_type} CHECK constraint in V1__initial_schema.sql.
 * <p>
 * Unlike {@link com.prateek.featureflag.environment.EnvironmentType}, the DB
 * values here ('BOOLEAN', 'PERCENTAGE', 'TARGETED') already match Java
 * enum-naming convention exactly, so no converter is needed — plain
 * {@code @Enumerated(EnumType.STRING)} maps 1:1.
 */
public enum FlagType {
    BOOLEAN,
    PERCENTAGE,
    TARGETED
}
