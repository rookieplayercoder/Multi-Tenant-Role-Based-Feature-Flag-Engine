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
import com.prateek.featureflag.segment.SegmentUser;
import com.prateek.featureflag.segment.SegmentUserId;
import com.prateek.featureflag.segment.SegmentUserRepository;
import com.prateek.featureflag.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RuleEvaluator}'s segment-membership CONDITION path
 * ({@code attribute == "segment"}), reached via {@code evaluateSegmentMembership}.
 * Covers behaviour that's specific to that path rather than to the general
 * attribute-comparison path already covered in {@code RuleEvaluatorTest}.
 */
@ExtendWith(MockitoExtension.class)
class RuleEvaluatorSegmentMembershipTest {

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

    private static FeatureRule segmentCondition(RuleOperator operator, String jsonValue) {
        FeatureRule rule = new FeatureRule(flagWithKey("checkout-flow"), RuleType.CONDITION);
        rule.setAttribute("segment");
        rule.setOperator(operator);
        rule.setValue(jsonValue);
        return rule;
    }

    @Nested
    class MemberMatches {

        @Test
        void equalsReturnsTrueWhenUserIsAMemberOfTheSegment() {
            UUID segmentId = UUID.randomUUID();
            FeatureRule rule = segmentCondition(RuleOperator.EQUALS, "\"" + segmentId + "\"");
            EvaluationContext context = new EvaluationContext("user-1", Map.of());
            SegmentUserId expectedId = new SegmentUserId(segmentId, "user-1");
            when(segmentUserRepository.findById(expectedId)).thenReturn(Optional.of(mock(SegmentUser.class)));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isTrue();
        }

        @Test
        void notEqualsReturnsFalseWhenUserIsAMemberOfTheSegment() {
            UUID segmentId = UUID.randomUUID();
            FeatureRule rule = segmentCondition(RuleOperator.NOT_EQUALS, "\"" + segmentId + "\"");
            EvaluationContext context = new EvaluationContext("user-1", Map.of());
            SegmentUserId expectedId = new SegmentUserId(segmentId, "user-1");
            when(segmentUserRepository.findById(expectedId)).thenReturn(Optional.of(mock(SegmentUser.class)));

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isFalse();
        }
    }

    @Nested
    class NonMemberFails {

        @Test
        void equalsReturnsFalseWhenUserIsNotAMemberOfTheSegment() {
            UUID segmentId = UUID.randomUUID();
            FeatureRule rule = segmentCondition(RuleOperator.EQUALS, "\"" + segmentId + "\"");
            EvaluationContext context = new EvaluationContext("user-1", Map.of());
            SegmentUserId expectedId = new SegmentUserId(segmentId, "user-1");
            when(segmentUserRepository.findById(expectedId)).thenReturn(Optional.empty());

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isFalse();
        }

        @Test
        void notEqualsReturnsTrueWhenUserIsNotAMemberOfTheSegment() {
            UUID segmentId = UUID.randomUUID();
            FeatureRule rule = segmentCondition(RuleOperator.NOT_EQUALS, "\"" + segmentId + "\"");
            EvaluationContext context = new EvaluationContext("user-1", Map.of());
            SegmentUserId expectedId = new SegmentUserId(segmentId, "user-1");
            when(segmentUserRepository.findById(expectedId)).thenReturn(Optional.empty());

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isTrue();
        }
    }

    @Nested
    class MultipleSegments {

        @Test
        void inOperatorMatchesWhenUserIsAMemberOfAnyListedSegment() {
            UUID firstSegmentId = UUID.randomUUID();
            UUID secondSegmentId = UUID.randomUUID();
            UUID thirdSegmentId = UUID.randomUUID();
            String value = "[\"%s\",\"%s\",\"%s\"]".formatted(firstSegmentId, secondSegmentId, thirdSegmentId);
            FeatureRule rule = segmentCondition(RuleOperator.IN, value);
            EvaluationContext context = new EvaluationContext("user-1", Map.of());

            when(segmentUserRepository.findById(new SegmentUserId(firstSegmentId, "user-1")))
                    .thenReturn(Optional.empty());
            when(segmentUserRepository.findById(new SegmentUserId(secondSegmentId, "user-1")))
                    .thenReturn(Optional.of(mock(SegmentUser.class)));
            // Third segment intentionally left unstubbed: anyMatch should short-circuit before reaching it.

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isTrue();
        }

        @Test
        void inOperatorFailsWhenUserIsAMemberOfNoneOfTheListedSegments() {
            UUID firstSegmentId = UUID.randomUUID();
            UUID secondSegmentId = UUID.randomUUID();
            String value = "[\"%s\",\"%s\"]".formatted(firstSegmentId, secondSegmentId);
            FeatureRule rule = segmentCondition(RuleOperator.IN, value);
            EvaluationContext context = new EvaluationContext("user-1", Map.of());

            when(segmentUserRepository.findById(new SegmentUserId(firstSegmentId, "user-1")))
                    .thenReturn(Optional.empty());
            when(segmentUserRepository.findById(new SegmentUserId(secondSegmentId, "user-1")))
                    .thenReturn(Optional.empty());

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isFalse();
        }

        @Test
        void notInOperatorMatchesWhenUserIsAMemberOfNoneOfTheListedSegments() {
            UUID firstSegmentId = UUID.randomUUID();
            UUID secondSegmentId = UUID.randomUUID();
            String value = "[\"%s\",\"%s\"]".formatted(firstSegmentId, secondSegmentId);
            FeatureRule rule = segmentCondition(RuleOperator.NOT_IN, value);
            EvaluationContext context = new EvaluationContext("user-1", Map.of());

            when(segmentUserRepository.findById(new SegmentUserId(firstSegmentId, "user-1")))
                    .thenReturn(Optional.empty());
            when(segmentUserRepository.findById(new SegmentUserId(secondSegmentId, "user-1")))
                    .thenReturn(Optional.empty());

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isTrue();
        }

        @Test
        void notInOperatorFailsWhenUserIsAMemberOfAtLeastOneListedSegment() {
            UUID firstSegmentId = UUID.randomUUID();
            UUID secondSegmentId = UUID.randomUUID();
            String value = "[\"%s\",\"%s\"]".formatted(firstSegmentId, secondSegmentId);
            FeatureRule rule = segmentCondition(RuleOperator.NOT_IN, value);
            EvaluationContext context = new EvaluationContext("user-1", Map.of());

            when(segmentUserRepository.findById(new SegmentUserId(firstSegmentId, "user-1")))
                    .thenReturn(Optional.of(mock(SegmentUser.class)));
            when(segmentUserRepository.findById(new SegmentUserId(secondSegmentId, "user-1")))
                    .thenReturn(Optional.empty());

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isFalse();
        }
    }

    @Nested
    class MalformedUuid {

        /**
         * {@code evaluateSegmentMembership} calls {@code UUID.fromString} directly
         * with no surrounding try/catch (unlike the numeric-comparison path, which
         * explicitly catches {@code NumberFormatException} to fail closed). A
         * malformed segment identifier therefore propagates as an uncaught
         * {@link IllegalArgumentException} rather than evaluating to {@code false}.
         * This test documents that current behaviour rather than asserting a
         * fail-closed outcome that the code doesn't actually implement.
         */
        @Test
        void equalsOperatorPropagatesIllegalArgumentExceptionForANonUuidValue() {
            FeatureRule rule = segmentCondition(RuleOperator.EQUALS, "\"not-a-valid-uuid\"");
            EvaluationContext context = new EvaluationContext("user-1", Map.of());

            assertThatThrownBy(() -> ruleEvaluator.evaluate(rule, context))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(segmentUserRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        }

        @Test
        void inOperatorPropagatesIllegalArgumentExceptionWhenAnyListedValueIsNotAUuid() {
            UUID validSegmentId = UUID.randomUUID();
            String value = "[\"%s\",\"not-a-valid-uuid\"]".formatted(validSegmentId);
            FeatureRule rule = segmentCondition(RuleOperator.IN, value);
            EvaluationContext context = new EvaluationContext("user-1", Map.of());
            when(segmentUserRepository.findById(new SegmentUserId(validSegmentId, "user-1")))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> ruleEvaluator.evaluate(rule, context))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class RepositoryInteractions {

        @Test
        void queriesRepositoryExactlyOnceWithTheExpectedCompositeKeyForASingleSegment() {
            UUID segmentId = UUID.randomUUID();
            FeatureRule rule = segmentCondition(RuleOperator.EQUALS, "\"" + segmentId + "\"");
            EvaluationContext context = new EvaluationContext("user-1", Map.of());
            SegmentUserId expectedId = new SegmentUserId(segmentId, "user-1");
            when(segmentUserRepository.findById(expectedId)).thenReturn(Optional.empty());

            ruleEvaluator.evaluate(rule, context);

            verify(segmentUserRepository, times(1)).findById(expectedId);
        }
    }
}
