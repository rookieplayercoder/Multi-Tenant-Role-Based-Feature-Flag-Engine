package com.prateek.featureflag.evaluation;

import com.prateek.featureflag.environment.Environment;
import com.prateek.featureflag.environment.EnvironmentType;
import com.prateek.featureflag.evaluation.RuleEvaluator.EvaluationContext;
import com.prateek.featureflag.flag.FeatureFlag;
import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.project.Project;
import com.prateek.featureflag.rules.FeatureRule;
import com.prateek.featureflag.rules.RuleOperator;
import com.prateek.featureflag.rules.RuleType;
import com.prateek.featureflag.segment.SegmentUserId;
import com.prateek.featureflag.segment.SegmentUserRepository;
import com.prateek.featureflag.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RuleEvaluator}. {@link SegmentUserRepository} is
 * mocked; {@link ObjectMapper} is a real instance since it's only used for
 * plain read-only JSON scalar/array decoding of condition values here, with
 * no need to stub behaviour.
 * <p>
 * Only CONDITION-node behaviour is covered, per this batch's scope; GROUP
 * node combination (AND/OR/NOT) is intentionally out of scope here.
 */
@ExtendWith(MockitoExtension.class)
class RuleEvaluatorTest {

    @Mock
    private SegmentUserRepository segmentUserRepository;

    private RuleEvaluator ruleEvaluator;

    @BeforeEach
    void setUp() {
        ruleEvaluator = new RuleEvaluator(segmentUserRepository, new ObjectMapper());
    }

    private static FeatureFlag flagWithKey(String key) {
        Organization organization = new Organization("Acme Inc", "acme");
        Project project = new Project(organization, "Web App", "web");
        Environment environment = new Environment(project, "Production", EnvironmentType.PROD);
        User createdBy = new User("creator@example.com", "hash", "Creator");
        return new FeatureFlag(environment, key, "Flag " + key, createdBy);
    }

    private static FeatureRule condition(String attribute, RuleOperator operator, String jsonValue,
                                         Integer rolloutPercentage) {
        FeatureRule rule = new FeatureRule(flagWithKey("checkout-flow"), RuleType.CONDITION);
        rule.setAttribute(attribute);
        rule.setOperator(operator);
        rule.setValue(jsonValue);
        rule.setRolloutPercentage(rolloutPercentage);
        return rule;
    }

    private static FeatureRule conditionOnFlag(FeatureFlag flag, String attribute, RuleOperator operator,
                                               String jsonValue, Integer rolloutPercentage) {
        FeatureRule rule = new FeatureRule(flag, RuleType.CONDITION);
        rule.setAttribute(attribute);
        rule.setOperator(operator);
        rule.setValue(jsonValue);
        rule.setRolloutPercentage(rolloutPercentage);
        return rule;
    }

    @Nested
    class EqualsAndNotEquals {

        @Test
        void equalsReturnsTrueWhenAttributeMatchesValue() {
            FeatureRule rule = condition("plan", RuleOperator.EQUALS, "\"gold\"", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("plan", "gold"));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isTrue();
        }

        @Test
        void equalsReturnsFalseWhenAttributeDoesNotMatchValue() {
            FeatureRule rule = condition("plan", RuleOperator.EQUALS, "\"gold\"", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("plan", "silver"));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isFalse();
        }

        @Test
        void notEqualsReturnsTrueWhenAttributeDiffersFromValue() {
            FeatureRule rule = condition("plan", RuleOperator.NOT_EQUALS, "\"gold\"", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("plan", "silver"));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isTrue();
        }

        @Test
        void notEqualsReturnsFalseWhenAttributeMatchesValue() {
            FeatureRule rule = condition("plan", RuleOperator.NOT_EQUALS, "\"gold\"", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("plan", "gold"));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isFalse();
        }
    }

    @Nested
    class InAndNotIn {

        @Test
        void inReturnsTrueWhenAttributeIsAMemberOfTheValueList() {
            FeatureRule rule = condition("plan", RuleOperator.IN, "[\"gold\",\"platinum\"]", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("plan", "platinum"));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isTrue();
        }

        @Test
        void inReturnsFalseWhenAttributeIsNotAMemberOfTheValueList() {
            FeatureRule rule = condition("plan", RuleOperator.IN, "[\"gold\",\"platinum\"]", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("plan", "bronze"));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isFalse();
        }

        @Test
        void notInReturnsTrueWhenAttributeIsNotAMemberOfTheValueList() {
            FeatureRule rule = condition("plan", RuleOperator.NOT_IN, "[\"gold\",\"platinum\"]", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("plan", "bronze"));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isTrue();
        }

        @Test
        void notInReturnsFalseWhenAttributeIsAMemberOfTheValueList() {
            FeatureRule rule = condition("plan", RuleOperator.NOT_IN, "[\"gold\",\"platinum\"]", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("plan", "gold"));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isFalse();
        }
    }

    @Nested
    class NumericComparisons {

        @Test
        void greaterThanReturnsTrueWhenActualExceedsValue() {
            FeatureRule rule = condition("age", RuleOperator.GREATER_THAN, "18", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("age", 21));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isTrue();
        }

        @Test
        void greaterThanReturnsFalseWhenActualDoesNotExceedValue() {
            FeatureRule rule = condition("age", RuleOperator.GREATER_THAN, "18", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("age", 16));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isFalse();
        }

        @Test
        void lessThanReturnsTrueWhenActualIsBelowValue() {
            FeatureRule rule = condition("age", RuleOperator.LESS_THAN, "18", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("age", 16));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isTrue();
        }

        @Test
        void lessThanReturnsFalseWhenActualIsNotBelowValue() {
            FeatureRule rule = condition("age", RuleOperator.LESS_THAN, "18", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("age", 21));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isFalse();
        }
    }

    @Nested
    class StringComparisons {

        @Test
        void containsReturnsTrueWhenActualIncludesValueAsSubstring() {
            FeatureRule rule = condition("email", RuleOperator.CONTAINS, "\"@acme.com\"", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("email", "jane@acme.com"));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isTrue();
        }

        @Test
        void containsReturnsFalseWhenActualDoesNotIncludeValueAsSubstring() {
            FeatureRule rule = condition("email", RuleOperator.CONTAINS, "\"@acme.com\"", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("email", "jane@other.com"));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isFalse();
        }
    }

    @Nested
    class InvalidValues {

        @Test
        void numericComparisonFailsClosedWhenActualAttributeIsNotNumeric() {
            FeatureRule rule = condition("age", RuleOperator.GREATER_THAN, "18", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("age", "not-a-number"));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isFalse();
        }

        @Test
        void numericComparisonFailsClosedWhenRuleValueIsNotNumeric() {
            FeatureRule rule = condition("age", RuleOperator.GREATER_THAN, "\"eighteen\"", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("age", 21));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isFalse();
        }
    }

    @Nested
    class NullAttributes {

        @Test
        void evaluateAttributeFailsClosedWhenContextDoesNotSupplyTheAttribute() {
            FeatureRule rule = condition("plan", RuleOperator.EQUALS, "\"gold\"", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("otherAttribute", "value"));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isFalse();
        }

        @Test
        void conditionWithNoAttributeOrOperatorIsVacuouslyTrue() {
            FeatureRule rule = condition(null, null, null, null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of());

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isTrue();
        }
    }

    @Nested
    class AnonymousUser {

        @Test
        void rolloutCheckFailsClosedWhenUserIdentifierIsNull() {
            FeatureRule rule = condition(null, null, null, 100);
            EvaluationContext context = new EvaluationContext(null, Map.of());

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isFalse();
        }

        @Test
        void segmentMembershipCheckFailsClosedWhenUserIdentifierIsNull() {
            FeatureRule rule = condition("segment", RuleOperator.EQUALS, "\"" + UUID.randomUUID() + "\"", null);
            EvaluationContext context = new EvaluationContext(null, Map.of());

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isFalse();
            verify(segmentUserRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    class FailClosedBehavior {

        @Test
        void attributeAndRolloutBothPassingIsRequiredForConditionToMatch() {
            FeatureFlag flag = flagWithKey("checkout-flow");
            FeatureRule rule = conditionOnFlag(flag, "plan", RuleOperator.EQUALS, "\"gold\"", 0);
            EvaluationContext context = new EvaluationContext("user-1", Map.of("plan", "gold"));

            boolean result = ruleEvaluator.evaluate(rule, context);

            // Attribute matches, but a 0% rollout means no bucket is ever < 0.
            assertThat(result).isFalse();
        }

        @Test
        void unrecognisedSegmentSkipsRepositoryLookupOnlyWhenUserIsAnonymous() {
            FeatureRule rule = condition("segment", RuleOperator.EQUALS, "\"" + UUID.randomUUID() + "\"", null);
            EvaluationContext anonymousContext = new EvaluationContext(null, Map.of());

            boolean result = ruleEvaluator.evaluate(rule, anonymousContext);

            assertThat(result).isFalse();
            verify(segmentUserRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        }

        @Test
        void segmentMembershipFailsClosedWhenUserIsNotAMember() {
            UUID segmentId = UUID.randomUUID();
            FeatureRule rule = condition("segment", RuleOperator.EQUALS, "\"" + segmentId + "\"", null);
            EvaluationContext context = new EvaluationContext("user-1", Map.of());
            SegmentUserId expectedId = new SegmentUserId(segmentId, "user-1");
            when(segmentUserRepository.findById(eq(expectedId))).thenReturn(Optional.empty());

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isFalse();
        }
    }
}