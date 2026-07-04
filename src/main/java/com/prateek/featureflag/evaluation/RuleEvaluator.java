package com.prateek.featureflag.evaluation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prateek.featureflag.rules.FeatureRule;
import com.prateek.featureflag.rules.LogicalOperator;
import com.prateek.featureflag.rules.RuleOperator;
import com.prateek.featureflag.segment.SegmentUserId;
import com.prateek.featureflag.segment.SegmentUserRepository;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recursively evaluates a {@link FeatureRule} tree against a single
 * {@link EvaluationContext}. Reads {@code childRules} via the entity's own
 * lazy collection rather than re-querying {@code FeatureRuleService} — this
 * only works because the whole recursive walk happens inside the caller's
 * (Hibernate session-bound) transaction; see
 * {@code FeatureFlagEvaluationService}.
 * <p>
 * A {@code CONDITION} node's attribute check and rollout-percentage check
 * are independent and both must pass — either can be absent (vacuously
 * true), which is what lets one node express pure attribute targeting,
 * pure percentage rollout, or both combined.
 */
@Component
public class RuleEvaluator {

    /** Reserved attribute name: value is one or more segment UUIDs, checked via segment membership, not the context map. */
    private static final String SEGMENT_ATTRIBUTE = "segment";

    private final SegmentUserRepository segmentUserRepository;
    private final ObjectMapper objectMapper;

    public RuleEvaluator(SegmentUserRepository segmentUserRepository, ObjectMapper objectMapper) {
        this.segmentUserRepository = segmentUserRepository;
        this.objectMapper = objectMapper;
    }

    public boolean evaluate(FeatureRule rule, EvaluationContext context) {
        return switch (rule.getRuleType()) {
            case GROUP -> evaluateGroup(rule, context);
            case CONDITION -> evaluateCondition(rule, context);
        };
    }

    private boolean evaluateGroup(FeatureRule group, EvaluationContext context) {
        List<FeatureRule> children = group.getChildRules();
        LogicalOperator operator = group.getLogicalOperator();
        if (operator == null || children.isEmpty()) {
            return false;
        }
        return switch (operator) {
            case AND -> children.stream().allMatch(child -> evaluate(child, context));
            case OR -> children.stream().anyMatch(child -> evaluate(child, context));
            case NOT -> !evaluate(children.get(0), context);
        };
    }

    private boolean evaluateCondition(FeatureRule condition, EvaluationContext context) {
        return evaluateAttribute(condition, context) && evaluateRollout(condition, context);
    }

    private boolean evaluateAttribute(FeatureRule condition, EvaluationContext context) {
        String attribute = condition.getAttribute();
        RuleOperator operator = condition.getOperator();
        if (attribute == null || operator == null) {
            return true; // no attribute constraint on this node
        }
        if (SEGMENT_ATTRIBUTE.equalsIgnoreCase(attribute)) {
            return evaluateSegmentMembership(condition, context);
        }

        Object actual = context.attributes().get(attribute);
        if (actual == null) {
            return false; // fail closed: can't match an attribute the context didn't supply
        }

        try {
            return switch (operator) {
                case EQUALS -> actual.toString().equals(readScalar(condition.getValue()));
                case NOT_EQUALS -> !actual.toString().equals(readScalar(condition.getValue()));
                case GREATER_THAN -> compareNumeric(actual, condition.getValue()) > 0;
                case LESS_THAN -> compareNumeric(actual, condition.getValue()) < 0;
                case IN -> readList(condition.getValue()).contains(actual.toString());
                case NOT_IN -> !readList(condition.getValue()).contains(actual.toString());
                case CONTAINS -> actual.toString().contains(readScalar(condition.getValue()));
            };
        } catch (NumberFormatException malformedComparison) {
            return false; // fail closed rather than propagate a 500 for bad numeric input
        }
    }

    private boolean evaluateSegmentMembership(FeatureRule condition, EvaluationContext context) {
        if (context.userIdentifier() == null) {
            return false;
        }
        RuleOperator operator = condition.getOperator();
        List<String> segmentIds = (operator == RuleOperator.IN || operator == RuleOperator.NOT_IN)
                ? readList(condition.getValue())
                : List.of(readScalar(condition.getValue()));

        boolean isMember = segmentIds.stream().anyMatch(segmentId -> segmentUserRepository
                .findById(new SegmentUserId(UUID.fromString(segmentId), context.userIdentifier()))
                .isPresent());

        boolean negate = operator == RuleOperator.NOT_IN || operator == RuleOperator.NOT_EQUALS;
        return negate != isMember;
    }

    private boolean evaluateRollout(FeatureRule condition, EvaluationContext context) {
        Integer rolloutPercentage = condition.getRolloutPercentage();
        if (rolloutPercentage == null) {
            return true; // no percentage gate on this node
        }
        if (context.userIdentifier() == null) {
            return false; // can't bucket an anonymous user
        }
        int bucket = bucketOf(condition.getFeatureFlag().getKey(), context.userIdentifier());
        return bucket < rolloutPercentage;
    }

    /** Deterministic 0-99 bucket for a (flagKey, userIdentifier) pair, stable across evaluations. */
    private int bucketOf(String flagKey, String userIdentifier) {
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

    private int compareNumeric(Object actual, String rawValue) {
        double actualValue = Double.parseDouble(actual.toString());
        double expectedValue = Double.parseDouble(readScalar(rawValue));
        return Double.compare(actualValue, expectedValue);
    }

    /** Reads a JSON-encoded scalar back to its plain string form; falls back to the raw string if not JSON. */
    private String readScalar(String rawJsonValue) {
        if (rawJsonValue == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(rawJsonValue);
            return node.isTextual() ? node.asText() : node.toString();
        } catch (Exception notJson) {
            return rawJsonValue;
        }
    }

    /** Reads a JSON array (for IN/NOT_IN) into a plain string list; a lone scalar becomes a single-element list. */
    private List<String> readList(String rawJsonValue) {
        if (rawJsonValue == null) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(rawJsonValue);
            if (node.isArray()) {
                List<String> values = new ArrayList<>();
                node.forEach(element -> values.add(element.isTextual() ? element.asText() : element.toString()));
                return values;
            }
            return List.of(node.isTextual() ? node.asText() : node.toString());
        } catch (Exception notJson) {
            return List.of(rawJsonValue);
        }
    }

    /**
     * Evaluation input: {@code userIdentifier} drives percentage bucketing and
     * segment membership; {@code attributes} drives attribute-based
     * CONDITION checks. {@code attributes} is defensively copied/null-safe.
     */
    public record EvaluationContext(String userIdentifier, Map<String, Object> attributes) {
        public EvaluationContext {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }
}
