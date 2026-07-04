package com.prateek.featureflag.rules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link FeatureRule}.
 * <p>
 * {@code findByFeatureFlagIdAndParentRuleIsNullOrderByPositionAsc} loads the
 * root nodes of a flag's rule tree in render order.
 * {@code findByParentRuleIdOrderByPositionAsc} loads a subtree's children
 * directly by parent id, without needing the parent entity already loaded.
 * Both are ordered per {@code idx_feature_rules_flag_position} /
 * {@code idx_feature_rules_parent_rule_id}.
 */
public interface FeatureRuleRepository extends JpaRepository<FeatureRule, UUID> {

    List<FeatureRule> findByFeatureFlagIdAndParentRuleIsNullOrderByPositionAsc(UUID featureFlagId);

    List<FeatureRule> findByParentRuleIdOrderByPositionAsc(UUID parentRuleId);
}
