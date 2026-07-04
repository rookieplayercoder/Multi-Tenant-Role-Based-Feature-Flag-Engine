package com.prateek.featureflag.rules;

/**
 * Node kind in the {@link FeatureRule} composite tree.
 * Matches {@code ck_feature_rules_type} in V1__initial_schema.sql.
 */
public enum RuleType {
    GROUP,
    CONDITION
}
