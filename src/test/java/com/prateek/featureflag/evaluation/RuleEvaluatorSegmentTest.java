package com.prateek.featureflag.evaluation;

import com.prateek.featureflag.rules.FeatureRule;
import com.prateek.featureflag.rules.RuleOperator;
import com.prateek.featureflag.rules.RuleType;
import com.prateek.featureflag.segment.SegmentUserId;
import com.prateek.featureflag.segment.SegmentUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Batch 2 verification: the "segment" reserved-attribute path in
 * {@link RuleEvaluator}, i.e. the end-to-end link between a membership row
 * created via {@code SegmentController}/{@code SegmentUserService} and a
 * live flag evaluation that targets it.
 */
@ExtendWith(MockitoExtension.class)
class RuleEvaluatorSegmentTest {

    @Mock
    private SegmentUserRepository segmentUserRepository;

    private RuleEvaluator ruleEvaluator;
    private UUID segmentId;

    @BeforeEach
    void setUp() {
        ruleEvaluator = new RuleEvaluator(segmentUserRepository, new ObjectMapper());
        segmentId = UUID.randomUUID();
    }

    private FeatureRule condition(RuleOperator operator, String value) {
        FeatureRule rule = new FeatureRule(null, RuleType.CONDITION);
        rule.setAttribute("segment");
        rule.setOperator(operator);
        rule.setValue(value);
        return rule;
    }

    @Test
    void equals_matchesWhenUserIsMember() {
        FeatureRule rule = condition(RuleOperator.EQUALS, "\"" + segmentId + "\"");
        when(segmentUserRepository.findById(new SegmentUserId(segmentId, "user-1")))
                .thenReturn(Optional.of(mockMembership()));

        boolean result = ruleEvaluator.evaluate(rule, new RuleEvaluator.EvaluationContext("user-1", null));

        assertThat(result).isTrue();
    }

    @Test
    void equals_failsWhenUserIsNotMember() {
        FeatureRule rule = condition(RuleOperator.EQUALS, "\"" + segmentId + "\"");
        when(segmentUserRepository.findById(new SegmentUserId(segmentId, "user-1")))
                .thenReturn(Optional.empty());

        boolean result = ruleEvaluator.evaluate(rule, new RuleEvaluator.EvaluationContext("user-1", null));

        assertThat(result).isFalse();
    }

    @Test
    void notEquals_isNegationOfMembership() {
        FeatureRule rule = condition(RuleOperator.NOT_EQUALS, "\"" + segmentId + "\"");
        when(segmentUserRepository.findById(new SegmentUserId(segmentId, "user-1")))
                .thenReturn(Optional.empty());

        boolean result = ruleEvaluator.evaluate(rule, new RuleEvaluator.EvaluationContext("user-1", null));

        assertThat(result).isTrue();
    }

    @Test
    void in_matchesIfMemberOfAnyListedSegment() {
        UUID otherSegmentId = UUID.randomUUID();
        FeatureRule rule = condition(RuleOperator.IN,
                "[\"" + segmentId + "\", \"" + otherSegmentId + "\"]");
        when(segmentUserRepository.findById(new SegmentUserId(segmentId, "user-1")))
                .thenReturn(Optional.empty());
        when(segmentUserRepository.findById(new SegmentUserId(otherSegmentId, "user-1")))
                .thenReturn(Optional.of(mockMembership()));

        boolean result = ruleEvaluator.evaluate(rule, new RuleEvaluator.EvaluationContext("user-1", null));

        assertThat(result).isTrue();
    }

    @Test
    void notIn_failsIfMemberOfAnyListedSegment() {
        FeatureRule rule = condition(RuleOperator.NOT_IN, "[\"" + segmentId + "\"]");
        when(segmentUserRepository.findById(new SegmentUserId(segmentId, "user-1")))
                .thenReturn(Optional.of(mockMembership()));

        boolean result = ruleEvaluator.evaluate(rule, new RuleEvaluator.EvaluationContext("user-1", null));

        assertThat(result).isFalse();
    }

    @Test
    void anonymousUser_neverMatchesSegmentCondition() {
        FeatureRule rule = condition(RuleOperator.EQUALS, "\"" + segmentId + "\"");

        boolean result = ruleEvaluator.evaluate(rule, new RuleEvaluator.EvaluationContext(null, null));

        assertThat(result).isFalse();
        // Should fail closed without ever touching the repository for an anonymous user.
        org.mockito.Mockito.verifyNoInteractions(segmentUserRepository);
    }

    @Test
    void malformedSegmentId_currentlyThrowsInsteadOfFailingClosed() {
        // GAP FOUND IN THIS BATCH: every other malformed-input path in
        // evaluateAttribute() (e.g. GREATER_THAN/LESS_THAN with a
        // non-numeric value) is wrapped in a try/catch that fails closed
        // (returns false) rather than propagating. evaluateSegmentMembership()
        // is the one exception — UUID.fromString() on a bad rule value throws
        // uncaught, which the evaluation endpoint would surface as a 400
        // instead of just treating the rule as non-matching. This test
        // documents current behavior; if evaluateSegmentMembership is later
        // hardened to fail closed like its siblings, flip this assertion to
        // expect `false` instead of the exception.
        FeatureRule rule = condition(RuleOperator.EQUALS, "\"not-a-uuid\"");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ruleEvaluator.evaluate(rule, new RuleEvaluator.EvaluationContext("user-1", null)));
    }

    private com.prateek.featureflag.segment.SegmentUser mockMembership() {
        return org.mockito.Mockito.mock(com.prateek.featureflag.segment.SegmentUser.class);
    }
}