package com.prateek.featureflag.rules;

import com.prateek.featureflag.flag.FeatureFlag;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service layer for {@link FeatureRule}. No soft delete — a rule's
 * lifecycle is owned by the flag's version history (see the entity's own
 * Javadoc), so {@code delete} here is a real delete; the DB's
 * {@code ON DELETE CASCADE} on {@code parent_rule_id} takes care of the
 * subtree. The GROUP-vs-CONDITION shape invariant is enforced by the DB
 * check constraint ({@code ck_feature_rules_group_shape}) and intentionally
 * not re-validated here, consistent with the entity being kept thin.
 */
@Service
@Transactional(readOnly = true)
public class FeatureRuleService {

    private final FeatureRuleRepository featureRuleRepository;

    public FeatureRuleService(FeatureRuleRepository featureRuleRepository) {
        this.featureRuleRepository = featureRuleRepository;
    }

    @Transactional
    public FeatureRule addGroupRule(FeatureFlag featureFlag, FeatureRule parentRule,
                                     LogicalOperator logicalOperator, int position) {
        FeatureRule rule = new FeatureRule(featureFlag, RuleType.GROUP);
        rule.setParentRule(parentRule);
        rule.setLogicalOperator(logicalOperator);
        rule.setPosition(position);
        return featureRuleRepository.save(rule);
    }

    @Transactional
    public FeatureRule addConditionRule(FeatureFlag featureFlag, FeatureRule parentRule, String attribute,
                                         RuleOperator operator, String value, Integer rolloutPercentage,
                                         int position) {
        FeatureRule rule = new FeatureRule(featureFlag, RuleType.CONDITION);
        rule.setParentRule(parentRule);
        rule.setAttribute(attribute);
        rule.setOperator(operator);
        rule.setValue(value);
        rule.setRolloutPercentage(rolloutPercentage);
        rule.setPosition(position);
        return featureRuleRepository.save(rule);
    }

    public List<FeatureRule> listRootRules(UUID featureFlagId) {
        return featureRuleRepository.findByFeatureFlagIdAndParentRuleIsNullOrderByPositionAsc(featureFlagId);
    }

    public List<FeatureRule> listChildRules(UUID parentRuleId) {
        return featureRuleRepository.findByParentRuleIdOrderByPositionAsc(parentRuleId);
    }

    @Transactional
    public FeatureRule updatePosition(UUID ruleId, int position) {
        FeatureRule rule = featureRuleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("Feature rule not found: " + ruleId));
        rule.setPosition(position);
        return featureRuleRepository.save(rule);
    }

    @Transactional
    public void delete(UUID ruleId) {
        FeatureRule rule = featureRuleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("Feature rule not found: " + ruleId));
        featureRuleRepository.delete(rule);
    }
}
