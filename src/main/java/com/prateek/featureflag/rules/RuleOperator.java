package com.prateek.featureflag.rules;

/**
 * Comparison operator used only on {@code CONDITION}-type {@link FeatureRule} nodes.
 * Matches {@code ck_feature_rules_operator} in V1__initial_schema.sql.
 */
public enum RuleOperator {
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    LESS_THAN,
    IN,
    NOT_IN,
    CONTAINS
}
