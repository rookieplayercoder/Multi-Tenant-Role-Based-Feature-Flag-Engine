package com.prateek.featureflag.rules;

import com.prateek.featureflag.flag.FeatureFlag;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Self-referencing tree node implementing the Composite pattern for
 * targeting rules. A {@code GROUP} node combines children under
 * {@link LogicalOperator}; a {@code CONDITION} node is a leaf comparing
 * {@code attribute} against {@code value} via {@link RuleOperator}. Maps
 * exactly to the {@code feature_rules} table in V1__initial_schema.sql.
 * <p>
 * The GROUP-vs-CONDITION "shape" invariant (a GROUP has no attribute/operator/
 * value; a CONDITION has no logicalOperator) is enforced at the database via
 * {@code ck_feature_rules_group_shape} and is intentionally not duplicated
 * with cross-field bean validation here, to keep the entity thin.
 * <p>
 * {@code featureFlag} is unidirectional since {@link FeatureFlag} is not
 * modified in this batch.
 */
@Entity
@Table(name = "feature_rules")
public class FeatureRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feature_flag_id", nullable = false, updatable = false)
    private FeatureFlag featureFlag;

    /**
     * Owning side of the self-referencing tree. LAZY explicitly set.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_rule_id")
    private FeatureRule parentRule;

    /**
     * Inverse side of the tree. PERSIST/MERGE only, no REMOVE/orphanRemoval —
     * consistent with the project-wide convention: subtree deletion goes
     * through an explicit, auditable service call rather than an implicit
     * cascading/orphan delete triggered by mutating this collection.
     */
    @OneToMany(mappedBy = "parentRule", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("position ASC")
    private List<FeatureRule> childRules = new ArrayList<>();

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 20)
    private RuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "logical_operator", length = 10)
    private LogicalOperator logicalOperator;

    @Size(max = 100)
    @Column(name = "attribute")
    private String attribute;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", length = 20)
    private RuleOperator operator;

    /**
     * Flexible typed value (string, number, or array — e.g. for the IN
     * operator). Backed by Hibernate 6's native JSON mapping onto the
     * JSONB column.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value")
    private String value;

    @Min(0)
    @Max(100)
    @Column(name = "rollout_percentage")
    private Integer rolloutPercentage;

    @NotNull
    @Column(name = "position", nullable = false)
    private Integer position = 0;

    protected FeatureRule() {
    }

    public FeatureRule(FeatureFlag featureFlag, RuleType ruleType) {
        this.featureFlag = featureFlag;
        this.ruleType = ruleType;
    }

    public UUID getId() {
        return id;
    }

    public FeatureFlag getFeatureFlag() {
        return featureFlag;
    }

    public FeatureRule getParentRule() {
        return parentRule;
    }

    public void setParentRule(FeatureRule parentRule) {
        this.parentRule = parentRule;
    }

    public List<FeatureRule> getChildRules() {
        return List.copyOf(childRules);
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public void setRuleType(RuleType ruleType) {
        this.ruleType = ruleType;
    }

    public LogicalOperator getLogicalOperator() {
        return logicalOperator;
    }

    public void setLogicalOperator(LogicalOperator logicalOperator) {
        this.logicalOperator = logicalOperator;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public RuleOperator getOperator() {
        return operator;
    }

    public void setOperator(RuleOperator operator) {
        this.operator = operator;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Integer getRolloutPercentage() {
        return rolloutPercentage;
    }

    public void setRolloutPercentage(Integer rolloutPercentage) {
        this.rolloutPercentage = rolloutPercentage;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FeatureRule other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "FeatureRule{id=%s, ruleType=%s, logicalOperator=%s, attribute='%s', operator=%s}"
                .formatted(id, ruleType, logicalOperator, attribute, operator);
    }
}
