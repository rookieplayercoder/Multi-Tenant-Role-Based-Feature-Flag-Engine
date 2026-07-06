-- ============================================================================
-- V2 — relax feature_rules CONDITION shape to allow pure rollout-only rules
-- ============================================================================
-- Module 8 (percentage rollouts) needs a CONDITION rule that carries only
-- rollout_percentage — e.g. "roll out to 25% of users" with no attribute
-- targeting at all, combined via a parent GROUP with a separate attribute
-- CONDITION (e.g. country == IN). RuleEvaluator already supports this: a
-- CONDITION's attribute check and rollout check are independent, each
-- vacuously true when its own fields are absent (see RuleEvaluator's own
-- Javadoc). The original ck_feature_rules_group_shape constraint didn't
-- allow for that: it required attribute AND operator to be non-null for
-- every CONDITION row, with no alternative path for rollout_percentage.
--
-- The new constraint requires a CONDITION row to have (attribute AND
-- operator) OR rollout_percentage OR both — never neither, since a
-- CONDITION with nothing set at all is meaningless. It also tightens the
-- GROUP branch to explicitly forbid rollout_percentage there too (the
-- original constraint didn't mention it, an unintended gap — GROUP nodes
-- never set it, so this only closes an unused path, not a behavior change).

ALTER TABLE feature_rules DROP CONSTRAINT ck_feature_rules_group_shape;

ALTER TABLE feature_rules ADD CONSTRAINT ck_feature_rules_group_shape CHECK (
    (rule_type = 'GROUP' AND logical_operator IS NOT NULL AND attribute IS NULL
        AND operator IS NULL AND value IS NULL AND rollout_percentage IS NULL)
    OR
    (rule_type = 'CONDITION' AND logical_operator IS NULL
        AND ((attribute IS NOT NULL AND operator IS NOT NULL) OR rollout_percentage IS NOT NULL))
);