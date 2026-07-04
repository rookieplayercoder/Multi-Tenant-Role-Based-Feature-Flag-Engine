package com.prateek.featureflag.rules;

/**
 * Boolean combinator used only on {@code GROUP}-type {@link FeatureRule} nodes.
 * Matches {@code ck_feature_rules_logical_operator} in V1__initial_schema.sql.
 */
public enum LogicalOperator {
    AND,
    OR,
    NOT
}
