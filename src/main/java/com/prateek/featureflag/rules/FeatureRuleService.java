package com.prateek.featureflag.rules;

import com.prateek.featureflag.audit.AuditLogService;
import com.prateek.featureflag.audit.AuditAction;
import com.prateek.featureflag.audit.ResourceType;
import com.prateek.featureflag.flag.FeatureFlag;
import com.prateek.featureflag.user.User;
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
 * <p>
 * State-changing operations are recorded via the existing
 * {@link AuditLogService}. {@code FeatureRuleController} calls
 * {@code addGroupRule}/{@code addConditionRule} (create), {@code updatePosition}
 * (reorder), and {@code delete} without an actor today, so each of those
 * keeps its original unlogged signature untouched and gets a new sibling
 * overload that accepts a {@code User actor} and logs — same pattern used
 * for {@code ProjectService}/{@code EnvironmentService}/{@code MemberService}.
 * <p>
 * There was no general field-update method here at all — the controller's
 * {@code PUT} handler bypasses this service entirely and edits the entity via
 * {@link FeatureRuleRepository} directly (see that controller's own Javadoc:
 * "the service is frozen this batch"). Since this service is no longer
 * frozen, a new {@code update(...)} method is added below, mirroring exactly
 * what that controller handler does field-by-field, plus the "update" audit
 * log this module asks for. It isn't wired into the controller (that would
 * mean modifying {@code FeatureRuleController}), but it's the one place
 * moving forward that can log a rule field update.
 */
@Service
@Transactional(readOnly = true)
public class FeatureRuleService {

    private static final ResourceType ENTITY_TYPE = ResourceType.FEATURE_RULE;

    private final FeatureRuleRepository featureRuleRepository;
    private final AuditLogService auditLogService;

    public FeatureRuleService(FeatureRuleRepository featureRuleRepository, AuditLogService auditLogService) {
        this.featureRuleRepository = featureRuleRepository;
        this.auditLogService = auditLogService;
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

    /**
     * Same as {@link #addGroupRule(FeatureFlag, FeatureRule, LogicalOperator, int)},
     * plus an audit log entry attributed to {@code actor}.
     */
    @Transactional
    public FeatureRule addGroupRule(FeatureFlag featureFlag, FeatureRule parentRule,
                                    LogicalOperator logicalOperator, int position, User actor) {
        FeatureRule rule = addGroupRule(featureFlag, parentRule, logicalOperator, position);
        recordAudit(rule, actor, AuditAction.FEATURE_RULE_CREATED);
        return rule;
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

    /**
     * Same as {@link #addConditionRule(FeatureFlag, FeatureRule, String, RuleOperator, String, Integer, int)},
     * plus an audit log entry attributed to {@code actor}.
     */
    @Transactional
    public FeatureRule addConditionRule(FeatureFlag featureFlag, FeatureRule parentRule, String attribute,
                                        RuleOperator operator, String value, Integer rolloutPercentage,
                                        int position, User actor) {
        FeatureRule rule = addConditionRule(
                featureFlag, parentRule, attribute, operator, value, rolloutPercentage, position);
        recordAudit(rule, actor, AuditAction.FEATURE_RULE_CREATED);
        return rule;
    }

    public List<FeatureRule> listRootRules(UUID featureFlagId) {
        return featureRuleRepository.findByFeatureFlagIdAndParentRuleIsNullOrderByPositionAsc(featureFlagId);
    }

    public List<FeatureRule> listChildRules(UUID parentRuleId) {
        return featureRuleRepository.findByParentRuleIdOrderByPositionAsc(parentRuleId);
    }

    /**
     * General field update, mirroring {@code FeatureRuleController}'s PUT
     * handler field-by-field: only non-null arguments are applied, and
     * {@code logicalOperator} only applies to {@code GROUP} rules while the
     * condition fields only apply to non-{@code GROUP} rules.
     */
    @Transactional
    public FeatureRule update(UUID ruleId, LogicalOperator logicalOperator, String attribute, RuleOperator operator,
                              String value, Integer rolloutPercentage, User actor) {
        FeatureRule rule = getByIdOrThrow(ruleId);
        if (rule.getRuleType() == RuleType.GROUP) {
            if (logicalOperator != null) {
                rule.setLogicalOperator(logicalOperator);
            }
        } else {
            if (attribute != null) {
                rule.setAttribute(attribute);
            }
            if (operator != null) {
                rule.setOperator(operator);
            }
            if (value != null) {
                rule.setValue(value);
            }
            if (rolloutPercentage != null) {
                rule.setRolloutPercentage(rolloutPercentage);
            }
        }
        FeatureRule saved = featureRuleRepository.save(rule);
        recordAudit(saved, actor, AuditAction.FEATURE_RULE_UPDATED);
        return saved;
    }

    @Transactional
    public FeatureRule updatePosition(UUID ruleId, int position) {
        FeatureRule rule = getByIdOrThrow(ruleId);
        rule.setPosition(position);
        return featureRuleRepository.save(rule);
    }

    /**
     * Same as {@link #updatePosition(UUID, int)}, plus an audit log entry
     * attributed to {@code actor}.
     */
    @Transactional
    public FeatureRule updatePosition(UUID ruleId, int position, User actor) {
        FeatureRule saved = updatePosition(ruleId, position);
        recordAudit(saved, actor, AuditAction.FEATURE_RULE_REORDERED);
        return saved;
    }

    @Transactional
    public void delete(UUID ruleId) {
        FeatureRule rule = getByIdOrThrow(ruleId);
        featureRuleRepository.delete(rule);
    }

    /**
     * Same as {@link #delete(UUID)}, plus an audit log entry attributed to
     * {@code actor}. The organization/flag reference is captured before the
     * delete so the log entry can still be written afterward.
     */
    @Transactional
    public void delete(UUID ruleId, User actor) {
        FeatureRule rule = getByIdOrThrow(ruleId);
        FeatureFlag featureFlag = rule.getFeatureFlag();
        featureRuleRepository.delete(rule);
        auditLogService.record(
                featureFlag.getEnvironment().getProject().getOrganization(), actor, AuditAction.FEATURE_RULE_DELETED,
                ENTITY_TYPE, ruleId, null);
    }

    private FeatureRule getByIdOrThrow(UUID ruleId) {
        return featureRuleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("Feature rule not found: " + ruleId));
    }

    /**
     * Hands a rule change to the existing {@link AuditLogService}. The
     * organization is reached via {@code FeatureFlag -> Environment -> Project},
     * since {@link FeatureRule} has no direct reference to one.
     */
    private void recordAudit(FeatureRule rule, User actor, AuditAction action) {
        auditLogService.record(
                rule.getFeatureFlag().getEnvironment().getProject().getOrganization(), actor, action, ENTITY_TYPE,
                rule.getId(), null);
    }
}