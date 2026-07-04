package com.prateek.featureflag.environment;

/**
 * The fixed set of environment kinds an {@link Environment} can represent.
 * Must stay in sync with the {@code ck_environments_key} CHECK constraint
 * in V1__initial_schema.sql, which restricts the column to lowercase
 * {@code 'dev' | 'test' | 'staging' | 'prod'}.
 * <p>
 * Enum constants are uppercase per Java convention; {@link EnvironmentTypeConverter}
 * handles translating to/from the DB's lowercase representation so the
 * schema's string convention and Java's enum convention don't have to match.
 */
public enum EnvironmentType {
    DEV,
    TEST,
    STAGING,
    PROD
}
