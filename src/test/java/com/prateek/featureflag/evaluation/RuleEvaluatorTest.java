package com.prateek.featureflag.evaluation;

import com.prateek.featureflag.flag.FeatureFlag;
import com.prateek.featureflag.environment.Environment;
import com.prateek.featureflag.environment.EnvironmentType;
import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.project.Project;
import com.prateek.featureflag.rules.FeatureRule;
import com.prateek.featureflag.rules.LogicalOperator;
import com.prateek.featureflag.rules.RuleOperator;
import com.prateek.featureflag.rules.RuleType;
import com.prateek.featureflag.segment.SegmentUserRepository;
import com.prateek.featureflag.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Covers everything in {@link RuleEvaluator} except the segment-membership
 * path, which already has dedicated coverage in {@code RuleEvaluatorSegmentTest}
 * from the previous batch.
 */
@ExtendWith(MockitoExtension.class)
class RuleEvaluatorTest {

    @Mock
    private SegmentUserRepository segmentUserRepository;

    private RuleEvaluator ruleEvaluator;
    private FeatureFlag flag;

    @BeforeEach
    void setUp() {
        ruleEvaluator = new RuleEvaluator(segmentUserRepository, new ObjectMapper());

        Organization organization = new Organization("Acme", "acme");
        setId(organization, UUID.randomUUID());
        Project project = new Project(organization, "Web", "web");
        setId(project, UUID.randomUUID());
        Environment environment = new Environment(project, "Development", EnvironmentType.DEV);
        setId(environment, UUID.randomUUID());
        User creator = new User("dev@example.com", "hash", "Dev");
        setId(creator, UUID.randomUUID());
        flag = new FeatureFlag(environment, "rollout-flag", "Rollout Flag", creator);
        setId(flag, UUID.randomUUID());
    }

    private FeatureRule condition(String attribute, RuleOperator operator, String jsonValue) {
        FeatureRule rule = new FeatureRule(flag, RuleType.CONDITION);
        rule.setAttribute(attribute);
        rule.setOperator(operator);
        rule.setValue(jsonValue);
        return rule;
    }

    private FeatureRule group(LogicalOperator operator, FeatureRule... children) {
        FeatureRule rule = new FeatureRule(flag, RuleType.GROUP);
        rule.setLogicalOperator(operator);
        for (FeatureRule child : children) {
            addChild(rule, child);
        }
        return rule;
    }

    private RuleEvaluator.EvaluationContext ctx(String userIdentifier, Map<String, Object> attributes) {
        return new RuleEvaluator.EvaluationContext(userIdentifier, attributes);
    }

    // ---- GROUP logic ----

    @Test
    void group_and_requiresAllChildrenToMatch() {
        FeatureRule allTrue = group(LogicalOperator.AND,
                condition("plan", RuleOperator.EQUALS, "\"pro\""),
                condition("region", RuleOperator.EQUALS, "\"us\""));

        assertThat(ruleEvaluator.evaluate(allTrue, ctx("u1", Map.of("plan", "pro", "region", "us")))).isTrue();
        assertThat(ruleEvaluator.evaluate(allTrue, ctx("u1", Map.of("plan", "pro", "region", "eu")))).isFalse();
    }

    @Test
    void group_or_requiresAnyChildToMatch() {
        FeatureRule anyTrue = group(LogicalOperator.OR,
                condition("plan", RuleOperator.EQUALS, "\"pro\""),
                condition("plan", RuleOperator.EQUALS, "\"enterprise\""));

        assertThat(ruleEvaluator.evaluate(anyTrue, ctx("u1", Map.of("plan", "enterprise")))).isTrue();
        assertThat(ruleEvaluator.evaluate(anyTrue, ctx("u1", Map.of("plan", "free")))).isFalse();
    }

    @Test
    void group_not_negatesItsSingleChild() {
        FeatureRule notPro = group(LogicalOperator.NOT, condition("plan", RuleOperator.EQUALS, "\"pro\""));

        assertThat(ruleEvaluator.evaluate(notPro, ctx("u1", Map.of("plan", "free")))).isTrue();
        assertThat(ruleEvaluator.evaluate(notPro, ctx("u1", Map.of("plan", "pro")))).isFalse();
    }

    @Test
    void group_withNoOperator_isFalse() {
        FeatureRule noOperator = new FeatureRule(flag, RuleType.GROUP);
        addChild(noOperator, condition("plan", RuleOperator.EQUALS, "\"pro\""));

        assertThat(ruleEvaluator.evaluate(noOperator, ctx("u1", Map.of("plan", "pro")))).isFalse();
    }

    @Test
    void group_withNoChildren_isFalse() {
        FeatureRule empty = group(LogicalOperator.AND);

        assertThat(ruleEvaluator.evaluate(empty, ctx("u1", Map.of()))).isFalse();
    }

    // ---- CONDITION attribute operators ----

    @Test
    void condition_equals_matchesExactString() {
        FeatureRule rule = condition("plan", RuleOperator.EQUALS, "\"pro\"");
        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of("plan", "pro")))).isTrue();
        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of("plan", "free")))).isFalse();
    }

    @Test
    void condition_notEquals_isNegationOfEquals() {
        FeatureRule rule = condition("plan", RuleOperator.NOT_EQUALS, "\"pro\"");
        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of("plan", "free")))).isTrue();
        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of("plan", "pro")))).isFalse();
    }

    @Test
    void condition_greaterThan_comparesNumerically() {
        FeatureRule rule = condition("age", RuleOperator.GREATER_THAN, "18");
        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of("age", 21)))).isTrue();
        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of("age", 16)))).isFalse();
    }

    @Test
    void condition_lessThan_comparesNumerically() {
        FeatureRule rule = condition("age", RuleOperator.LESS_THAN, "18");
        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of("age", 16)))).isTrue();
        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of("age", 21)))).isFalse();
    }

    @Test
    void condition_in_matchesAnyListedValue() {
        FeatureRule rule = condition("plan", RuleOperator.IN, "[\"pro\",\"enterprise\"]");
        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of("plan", "enterprise")))).isTrue();
        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of("plan", "free")))).isFalse();
    }

    @Test
    void condition_notIn_isNegationOfIn() {
        FeatureRule rule = condition("plan", RuleOperator.NOT_IN, "[\"pro\",\"enterprise\"]");
        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of("plan", "free")))).isTrue();
        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of("plan", "pro")))).isFalse();
    }

    @Test
    void condition_contains_matchesSubstring() {
        FeatureRule rule = condition("email", RuleOperator.CONTAINS, "\"@acme.com\"");
        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of("email", "dev@acme.com")))).isTrue();
        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of("email", "dev@other.com")))).isFalse();
    }

    @Test
    void condition_missingAttributeInContext_failsClosed() {
        FeatureRule rule = condition("plan", RuleOperator.EQUALS, "\"pro\"");
        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of()))).isFalse();
    }

    @Test
    void condition_malformedNumericComparison_failsClosedInsteadOfThrowing() {
        FeatureRule rule = condition("age", RuleOperator.GREATER_THAN, "18");
        // "actual" attribute value isn't numeric -> NumberFormatException is caught internally.
        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of("age", "not-a-number")))).isFalse();
    }

    @Test
    void condition_withNoAttributeOrOperator_isVacuouslyTrue() {
        FeatureRule rule = new FeatureRule(flag, RuleType.CONDITION);
        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of()))).isTrue();
    }

    // ---- Rollout percentage ----

    @Test
    void rollout_hundredPercent_alwaysMatchesAnyUser() {
        FeatureRule rule = new FeatureRule(flag, RuleType.CONDITION);
        rule.setRolloutPercentage(100);

        for (String user : List.of("alice", "bob", "carol", "dave-1234")) {
            assertThat(ruleEvaluator.evaluate(rule, ctx(user, Map.of())))
                    .as("user=" + user).isTrue();
        }
    }

    @Test
    void rollout_zeroPercent_neverMatchesAnyUser() {
        FeatureRule rule = new FeatureRule(flag, RuleType.CONDITION);
        rule.setRolloutPercentage(0);

        for (String user : List.of("alice", "bob", "carol", "dave-1234")) {
            assertThat(ruleEvaluator.evaluate(rule, ctx(user, Map.of())))
                    .as("user=" + user).isFalse();
        }
    }

    @Test
    void rollout_anonymousUser_failsClosed() {
        FeatureRule rule = new FeatureRule(flag, RuleType.CONDITION);
        rule.setRolloutPercentage(50);

        assertThat(ruleEvaluator.evaluate(rule, ctx(null, Map.of()))).isFalse();
    }

    @Test
    void rollout_isDeterministic_sameUserAndFlagAlwaysBucketsTheSame() {
        FeatureRule rule = new FeatureRule(flag, RuleType.CONDITION);
        rule.setRolloutPercentage(50);
        RuleEvaluator.EvaluationContext context = ctx("stable-user-42", Map.of());

        boolean first = ruleEvaluator.evaluate(rule, context);
        for (int i = 0; i < 10; i++) {
            assertThat(ruleEvaluator.evaluate(rule, context)).isEqualTo(first);
        }
    }

    @Test
    void attributeAndRollout_bothMustPass() {
        FeatureRule rule = condition("plan", RuleOperator.EQUALS, "\"pro\"");
        rule.setRolloutPercentage(0); // attribute matches, but rollout gate always closed

        assertThat(ruleEvaluator.evaluate(rule, ctx("u1", Map.of("plan", "pro")))).isFalse();
    }

    @Test
    void nonSegmentCondition_neverTouchesSegmentRepository() {
        FeatureRule rule = condition("plan", RuleOperator.EQUALS, "\"pro\"");
        ruleEvaluator.evaluate(rule, ctx("u1", Map.of("plan", "pro")));
        verifyNoInteractions(segmentUserRepository);
    }

    // ---- helpers ----

    private static void addChild(FeatureRule parent, FeatureRule child) {
        child.setParentRule(parent);
        try {
            Field field = FeatureRule.class.getDeclaredField("childRules");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<FeatureRule> children = (List<FeatureRule>) field.get(parent);
            children.add(child);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setId(Object entity, UUID id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
