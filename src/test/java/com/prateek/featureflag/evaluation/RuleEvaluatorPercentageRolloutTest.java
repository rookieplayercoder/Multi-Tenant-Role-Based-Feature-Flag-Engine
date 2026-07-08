package com.prateek.featureflag.evaluation;

import com.prateek.featureflag.environment.Environment;
import com.prateek.featureflag.environment.EnvironmentType;
import com.prateek.featureflag.evaluation.RuleEvaluator.EvaluationContext;
import com.prateek.featureflag.flag.FeatureFlag;
import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.project.Project;
import com.prateek.featureflag.rules.FeatureRule;
import com.prateek.featureflag.rules.RuleType;
import com.prateek.featureflag.segment.SegmentUserRepository;
import com.prateek.featureflag.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link RuleEvaluator}'s percentage-rollout bucketing
 * ({@code evaluateRollout}/{@code bucketOf}), reached via a CONDITION node
 * with only {@code rolloutPercentage} set (no attribute/operator, so
 * {@code evaluateAttribute} is vacuously true and the rollout check alone
 * decides the result).
 * <p>
 * {@code bucketOf} is private with no test seam, so {@link #bucketOf}
 * below independently replicates the documented SHA-256 formula to compute
 * expected buckets for specific identifiers. This lets boundary tests
 * assert against an exact, non-flaky expected outcome rather than
 * depending on percentages that happen to work for an arbitrary string.
 */
@ExtendWith(MockitoExtension.class)
class RuleEvaluatorPercentageRolloutTest {

    private static final String FLAG_KEY = "checkout-flow";

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

    private static FeatureRule rolloutCondition(FeatureFlag flag, Integer rolloutPercentage) {
        FeatureRule rule = new FeatureRule(flag, RuleType.CONDITION);
        rule.setRolloutPercentage(rolloutPercentage);
        return rule;
    }

    /** Independent re-implementation of {@code RuleEvaluator.bucketOf}, used only to compute expected values. */
    private static int bucketOf(String flagKey, String userIdentifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((flagKey + ":" + userIdentifier).getBytes(StandardCharsets.UTF_8));
            int firstFourBytes = ((hash[0] & 0xFF) << 24) | ((hash[1] & 0xFF) << 16)
                    | ((hash[2] & 0xFF) << 8) | (hash[3] & 0xFF);
            return Math.floorMod(firstFourBytes, 100);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Nested
    class DeterministicHashing {

        @Test
        void sameFlagAndUserProduceTheSameResultAcrossRepeatedEvaluations() {
            FeatureFlag flag = flagWithKey(FLAG_KEY);
            FeatureRule rule = rolloutCondition(flag, 50);
            EvaluationContext context = new EvaluationContext("user-42", Map.of());

            boolean firstResult = ruleEvaluator.evaluate(rule, context);
            boolean secondResult = ruleEvaluator.evaluate(rule, context);
            boolean thirdResult = ruleEvaluator.evaluate(rule, context);

            assertThat(secondResult).isEqualTo(firstResult);
            assertThat(thirdResult).isEqualTo(firstResult);
        }

        @Test
        void resultMatchesTheDocumentedShaTwoFiveSixBucketingFormula() {
            FeatureFlag flag = flagWithKey(FLAG_KEY);
            int expectedBucket = bucketOf(FLAG_KEY, "user-42");
            // Set the threshold one above the expected bucket so it must match.
            FeatureRule rule = rolloutCondition(flag, expectedBucket + 1);
            EvaluationContext context = new EvaluationContext("user-42", Map.of());

            boolean result = ruleEvaluator.evaluate(rule, context);

            assertThat(result).isTrue();
        }
    }

    @Nested
    class ZeroPercent {

        @Test
        void neverMatchesRegardlessOfUserIdentifier() {
            FeatureFlag flag = flagWithKey(FLAG_KEY);
            FeatureRule rule = rolloutCondition(flag, 0);

            for (String userIdentifier : new String[] {"user-1", "user-2", "user-3", "user-42", "user-999"}) {
                boolean result = ruleEvaluator.evaluate(rule, new EvaluationContext(userIdentifier, Map.of()));
                assertThat(result).as("0%% rollout for %s", userIdentifier).isFalse();
            }
        }
    }

    @Nested
    class FiftyPercent {

        @Test
        void matchesOnlyWhenComputedBucketIsBelowFifty() {
            FeatureFlag flag = flagWithKey(FLAG_KEY);
            FeatureRule rule = rolloutCondition(flag, 50);

            for (String userIdentifier : new String[] {"user-1", "user-2", "user-3", "user-42", "user-999"}) {
                boolean expected = bucketOf(FLAG_KEY, userIdentifier) < 50;
                boolean result = ruleEvaluator.evaluate(rule, new EvaluationContext(userIdentifier, Map.of()));
                assertThat(result).as("50%% rollout for %s", userIdentifier).isEqualTo(expected);
            }
        }
    }

    @Nested
    class HundredPercent {

        @Test
        void alwaysMatchesRegardlessOfUserIdentifier() {
            FeatureFlag flag = flagWithKey(FLAG_KEY);
            FeatureRule rule = rolloutCondition(flag, 100);

            for (String userIdentifier : new String[] {"user-1", "user-2", "user-3", "user-42", "user-999"}) {
                boolean result = ruleEvaluator.evaluate(rule, new EvaluationContext(userIdentifier, Map.of()));
                assertThat(result).as("100%% rollout for %s", userIdentifier).isTrue();
            }
        }
    }

    @Nested
    class BoundaryValues {

        @Test
        void doesNotMatchWhenPercentageEqualsTheComputedBucketExactly() {
            FeatureFlag flag = flagWithKey(FLAG_KEY);
            int bucket = bucketOf(FLAG_KEY, "user-42");
            FeatureRule rule = rolloutCondition(flag, bucket);

            boolean result = ruleEvaluator.evaluate(rule, new EvaluationContext("user-42", Map.of()));

            // bucket < bucket is always false: the boundary itself is excluded.
            assertThat(result).isFalse();
        }

        @Test
        void matchesWhenPercentageIsOneAboveTheComputedBucket() {
            FeatureFlag flag = flagWithKey(FLAG_KEY);
            int bucket = bucketOf(FLAG_KEY, "user-42");
            FeatureRule rule = rolloutCondition(flag, bucket + 1);

            boolean result = ruleEvaluator.evaluate(rule, new EvaluationContext("user-42", Map.of()));

            assertThat(result).isTrue();
        }
    }

    @Nested
    class SameUserSameResult {

        @Test
        void identicalUserAndFlagProduceIdenticalOutcomeOnRepeatedCalls() {
            FeatureFlag flag = flagWithKey(FLAG_KEY);
            FeatureRule rule = rolloutCondition(flag, 30);
            EvaluationContext context = new EvaluationContext("user-777", Map.of());

            boolean[] results = new boolean[5];
            for (int i = 0; i < results.length; i++) {
                results[i] = ruleEvaluator.evaluate(rule, context);
            }

            assertThat(results).containsOnly(results[0]);
        }
    }

    @Nested
    class DifferentUsers {

        @Test
        void distinctUserIdentifiersCanFallOnEitherSideOfTheThreshold() {
            FeatureFlag flag = flagWithKey(FLAG_KEY);
            FeatureRule rule = rolloutCondition(flag, 50);
            String[] candidateUsers = {
                    "user-1", "user-2", "user-3", "user-4", "user-5",
                    "user-6", "user-7", "user-8", "user-9", "user-10"
            };

            boolean sawTrue = false;
            boolean sawFalse = false;
            for (String userIdentifier : candidateUsers) {
                boolean result = ruleEvaluator.evaluate(rule, new EvaluationContext(userIdentifier, Map.of()));
                sawTrue = sawTrue || result;
                sawFalse = sawFalse || !result;
            }

            // With 10 independently-hashed identifiers at a 50% threshold, both
            // outcomes are expected to appear — this is not a coin-flip assertion,
            // it directly reflects the SHA-256 bucket spread across this fixed set.
            assertThat(sawTrue).isTrue();
            assertThat(sawFalse).isTrue();
        }
    }

    @Nested
    class InvalidPercentage {

        @Test
        void belowZeroNeverMatchesSinceBucketIsAlwaysNonNegative() {
            FeatureFlag flag = flagWithKey(FLAG_KEY);
            FeatureRule rule = rolloutCondition(flag, -10);

            boolean result = ruleEvaluator.evaluate(rule, new EvaluationContext("user-1", Map.of()));

            assertThat(result).isFalse();
        }

        @Test
        void aboveOneHundredAlwaysMatchesSinceBucketIsAlwaysBelowOneHundred() {
            FeatureFlag flag = flagWithKey(FLAG_KEY);
            FeatureRule rule = rolloutCondition(flag, 150);

            boolean result = ruleEvaluator.evaluate(rule, new EvaluationContext("user-1", Map.of()));

            assertThat(result).isTrue();
        }

        @Test
        void nullPercentageMeansNoRolloutGateAndDoesNotConsultSegmentRepository() {
            FeatureFlag flag = flagWithKey(FLAG_KEY);
            FeatureRule rule = rolloutCondition(flag, null);

            boolean result = ruleEvaluator.evaluate(rule, new EvaluationContext("user-1", Map.of()));

            assertThat(result).isTrue();
            verifyNoInteractions(segmentUserRepository);
        }
    }
}