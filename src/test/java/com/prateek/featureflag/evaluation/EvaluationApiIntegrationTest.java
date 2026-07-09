package com.prateek.featureflag.evaluation;

import com.prateek.featureflag.auth.dto.LoginResponse;
import com.prateek.featureflag.auth.dto.RegisterRequest;
import com.prateek.featureflag.environment.EnvironmentType;
import com.prateek.featureflag.environment.dto.CreateEnvironmentRequest;
import com.prateek.featureflag.evaluation.dto.EvaluateFlagRequest;
import com.prateek.featureflag.flag.FlagType;
import com.prateek.featureflag.flag.dto.ChangeFlagTypeRequest;
import com.prateek.featureflag.flag.dto.CreateFeatureFlagRequest;
import com.prateek.featureflag.organization.dto.CreateOrganizationRequest;
import com.prateek.featureflag.project.dto.CreateProjectRequest;
import com.prateek.featureflag.rules.RuleOperator;
import com.prateek.featureflag.rules.RuleType;
import com.prateek.featureflag.rules.dto.CreateFeatureRuleRequest;
import com.prateek.featureflag.segment.dto.AddSegmentMemberRequest;
import com.prateek.featureflag.segment.dto.CreateSegmentRequest;
import com.prateek.featureflag.support.AbstractIntegrationTestBase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the dashboard evaluation endpoint,
 * {@code POST /api/environments/{environmentId}/evaluate}
 * ({@link EvaluationController}). Builds real organization -> project ->
 * environment -> flag -> rule/segment chains through the actual REST API
 * (no shortcuts through services/repositories directly) so the whole stack
 * — including {@code FeatureRuleService}, {@code RuleEvaluator}, and
 * {@code SegmentUserService} — is exercised exactly as it runs in
 * production.
 * <p>
 * Deliberately NOT {@code @Transactional} at the class level, unlike the
 * other integration test classes in this batch. {@code evaluate()}
 * ultimately calls {@code FlagEvaluationMetricService.record(...)}, which
 * is {@code @Transactional(propagation = Propagation.REQUIRES_NEW)} by
 * design (see that class's own Javadoc). {@code REQUIRES_NEW} opens a
 * second transaction on a separate physical connection — if this test
 * class wrapped each test in its own outer transaction, that second
 * connection would try to write a {@code flag_evaluation_metrics} row
 * referencing a {@code feature_flags} row that's still uncommitted from
 * this test's own setup, and fail its foreign key check (Postgres MVCC:
 * an uncommitted row from one transaction isn't visible to another).
 * Letting each MockMvc call commit for real, as it would in production,
 * avoids that mismatch. Test data isn't cleaned up between methods as a
 * result, but every helper here generates UUID-suffixed emails/slugs/keys,
 * so nothing collides across test methods, and this only ever runs
 * against the ephemeral, JVM-lifetime Testcontainers instance from
 * {@link AbstractIntegrationTestBase}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Disabled("Temporarily disabled while investigating Testcontainers environment issue")
class EvaluationApiIntegrationTest extends AbstractIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String uniqueSlug() {
        return "org-" + UUID.randomUUID();
    }

    private static String uniqueProjectKey() {
        return "proj-" + UUID.randomUUID();
    }

    private static String uniqueFlagKey() {
        return "flag-" + UUID.randomUUID();
    }

    private String registerNewUser() throws Exception {
        RegisterRequest request = new RegisterRequest(uniqueEmail(), "correct-horse-1", "Test User");
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        LoginResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class);
        return response.accessToken();
    }

    private UUID createOrganizationAs(String token) throws Exception {
        CreateOrganizationRequest request = new CreateOrganizationRequest("Acme Inc", uniqueSlug());
        MvcResult result = mockMvc.perform(post("/api/organizations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private UUID createProjectAs(String token, UUID organizationId) throws Exception {
        CreateProjectRequest request = new CreateProjectRequest("Web App", uniqueProjectKey());
        MvcResult result = mockMvc.perform(post("/api/organizations/{organizationId}/projects", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private UUID createEnvironmentAs(String token, UUID projectId) throws Exception {
        CreateEnvironmentRequest request = new CreateEnvironmentRequest("Production", EnvironmentType.PROD);
        MvcResult result = mockMvc.perform(post("/api/projects/{projectId}/environments", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private UUID createFlagAs(String token, UUID environmentId, String key) throws Exception {
        CreateFeatureFlagRequest request = new CreateFeatureFlagRequest(key, "Test Flag", null);
        MvcResult result = mockMvc.perform(post("/api/environments/{environmentId}/flags", environmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private void enableFlagAs(String token, UUID flagId) throws Exception {
        mockMvc.perform(post("/api/flags/{flagId}/enable", flagId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void changeFlagTypeAs(String token, UUID flagId, FlagType flagType) throws Exception {
        ChangeFlagTypeRequest request = new ChangeFlagTypeRequest(flagType);
        mockMvc.perform(put("/api/flags/{flagId}/type", flagId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void addConditionRuleAs(String token, UUID flagId, String attribute, RuleOperator operator,
                                    String jsonValue, Integer rolloutPercentage, int position) throws Exception {
        CreateFeatureRuleRequest request = new CreateFeatureRuleRequest(
                RuleType.CONDITION, null, null, attribute, operator, jsonValue, rolloutPercentage, position);
        mockMvc.perform(post("/api/flags/{flagId}/rules", flagId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private UUID createSegmentAs(String token, UUID organizationId, String name) throws Exception {
        CreateSegmentRequest request = new CreateSegmentRequest(name, null);
        MvcResult result = mockMvc.perform(post("/api/organizations/{organizationId}/segments", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private void addSegmentMemberAs(String token, UUID segmentId, String userIdentifier) throws Exception {
        AddSegmentMemberRequest request = new AddSegmentMemberRequest(userIdentifier);
        mockMvc.perform(post("/api/segments/{segmentId}/members", segmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private UUID idOf(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }

    /** Registers a fresh owner and builds an org/project/environment chain, returning the token, org ID, and environment ID. */
    private record Fixture(String token, UUID organizationId, UUID environmentId) {
    }

    private Fixture setUpEnvironment() throws Exception {
        String token = registerNewUser();
        UUID organizationId = createOrganizationAs(token);
        UUID projectId = createProjectAs(token, organizationId);
        UUID environmentId = createEnvironmentAs(token, projectId);
        return new Fixture(token, organizationId, environmentId);
    }

    @Nested
    class EvaluateExistingFlag {

        @Test
        void evaluatingAnEnabledBooleanFlagReturnsTrueWithBooleanEnabledReason() throws Exception {
            Fixture fixture = setUpEnvironment();
            String key = uniqueFlagKey();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), key);
            enableFlagAs(fixture.token(), flagId);
            EvaluateFlagRequest request = new EvaluateFlagRequest(key, "user-1", null);

            mockMvc.perform(post("/api/environments/{environmentId}/evaluate", fixture.environmentId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.flagId").value(flagId.toString()))
                    .andExpect(jsonPath("$.flagKey").value(key))
                    .andExpect(jsonPath("$.value").value(true))
                    .andExpect(jsonPath("$.reason").value("BOOLEAN_ENABLED"));
        }
    }

    @Nested
    class MissingFlag {

        @Test
        void evaluatingAFlagKeyThatDoesNotExistInTheEnvironmentReturnsNotFound() throws Exception {
            Fixture fixture = setUpEnvironment();
            EvaluateFlagRequest request = new EvaluateFlagRequest("does-not-exist", "user-1", null);

            mockMvc.perform(post("/api/environments/{environmentId}/evaluate", fixture.environmentId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class DisabledFlag {

        @Test
        void evaluatingADisabledFlagReturnsFalseWithFlagDisabledReasonRegardlessOfType() throws Exception {
            Fixture fixture = setUpEnvironment();
            String key = uniqueFlagKey();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), key);
            // Never enabled — new flags default to disabled, so this covers
            // the FLAG_DISABLED short-circuit before FlagType is even consulted.
            EvaluateFlagRequest request = new EvaluateFlagRequest(key, "user-1", null);

            mockMvc.perform(post("/api/environments/{environmentId}/evaluate", fixture.environmentId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.value").value(false))
                    .andExpect(jsonPath("$.reason").value("FLAG_DISABLED"));
        }
    }

    @Nested
    class TargetedRule {

        @Test
        void evaluatingATargetedFlagWithNoRulesDefinedReturnsTrueWithNoRulesDefinedReason() throws Exception {
            Fixture fixture = setUpEnvironment();
            String key = uniqueFlagKey();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), key);
            enableFlagAs(fixture.token(), flagId);
            changeFlagTypeAs(fixture.token(), flagId, FlagType.TARGETED);
            EvaluateFlagRequest request = new EvaluateFlagRequest(key, "user-1", null);

            mockMvc.perform(post("/api/environments/{environmentId}/evaluate", fixture.environmentId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.value").value(true))
                    .andExpect(jsonPath("$.reason").value("NO_RULES_DEFINED"));
        }

        @Test
        void evaluatingATargetedFlagWhereTheAttributeMatchesReturnsTrueWithRuleMatchedReason() throws Exception {
            Fixture fixture = setUpEnvironment();
            String key = uniqueFlagKey();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), key);
            enableFlagAs(fixture.token(), flagId);
            changeFlagTypeAs(fixture.token(), flagId, FlagType.TARGETED);
            addConditionRuleAs(fixture.token(), flagId, "plan", RuleOperator.EQUALS, "\"gold\"", null, 0);
            EvaluateFlagRequest request = new EvaluateFlagRequest(key, "user-1", Map.of("plan", "gold"));

            mockMvc.perform(post("/api/environments/{environmentId}/evaluate", fixture.environmentId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.value").value(true))
                    .andExpect(jsonPath("$.reason").value("RULE_MATCHED"));
        }

        @Test
        void evaluatingATargetedFlagWhereTheAttributeDoesNotMatchReturnsFalseWithNoRuleMatchedReason() throws Exception {
            Fixture fixture = setUpEnvironment();
            String key = uniqueFlagKey();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), key);
            enableFlagAs(fixture.token(), flagId);
            changeFlagTypeAs(fixture.token(), flagId, FlagType.TARGETED);
            addConditionRuleAs(fixture.token(), flagId, "plan", RuleOperator.EQUALS, "\"gold\"", null, 0);
            EvaluateFlagRequest request = new EvaluateFlagRequest(key, "user-1", Map.of("plan", "silver"));

            mockMvc.perform(post("/api/environments/{environmentId}/evaluate", fixture.environmentId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.value").value(false))
                    .andExpect(jsonPath("$.reason").value("NO_RULE_MATCHED"));
        }
    }

    @Nested
    class PercentageRollout {

        @Test
        void evaluatingAPercentageFlagWithAHundredPercentRolloutAlwaysMatches() throws Exception {
            Fixture fixture = setUpEnvironment();
            String key = uniqueFlagKey();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), key);
            enableFlagAs(fixture.token(), flagId);
            changeFlagTypeAs(fixture.token(), flagId, FlagType.PERCENTAGE);
            addConditionRuleAs(fixture.token(), flagId, null, null, null, 100, 0);
            EvaluateFlagRequest request = new EvaluateFlagRequest(key, "user-1", null);

            mockMvc.perform(post("/api/environments/{environmentId}/evaluate", fixture.environmentId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.value").value(true))
                    .andExpect(jsonPath("$.reason").value("RULE_MATCHED"));
        }

        @Test
        void evaluatingAPercentageFlagWithAZeroPercentRolloutNeverMatches() throws Exception {
            Fixture fixture = setUpEnvironment();
            String key = uniqueFlagKey();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), key);
            enableFlagAs(fixture.token(), flagId);
            changeFlagTypeAs(fixture.token(), flagId, FlagType.PERCENTAGE);
            addConditionRuleAs(fixture.token(), flagId, null, null, null, 0, 0);
            EvaluateFlagRequest request = new EvaluateFlagRequest(key, "user-1", null);

            mockMvc.perform(post("/api/environments/{environmentId}/evaluate", fixture.environmentId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.value").value(false))
                    .andExpect(jsonPath("$.reason").value("NO_RULE_MATCHED"));
        }

        @Test
        void evaluatingAPercentageFlagWithNoUserIdentifierFailsClosed() throws Exception {
            // rolloutPercentage=100 would always match for a real user, but
            // an anonymous evaluation still fails closed per RuleEvaluator.
            Fixture fixture = setUpEnvironment();
            String key = uniqueFlagKey();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), key);
            enableFlagAs(fixture.token(), flagId);
            changeFlagTypeAs(fixture.token(), flagId, FlagType.PERCENTAGE);
            addConditionRuleAs(fixture.token(), flagId, null, null, null, 100, 0);
            EvaluateFlagRequest request = new EvaluateFlagRequest(key, null, null);

            mockMvc.perform(post("/api/environments/{environmentId}/evaluate", fixture.environmentId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.value").value(false))
                    .andExpect(jsonPath("$.reason").value("NO_RULE_MATCHED"));
        }
    }

    @Nested
    class SegmentEvaluation {

        @Test
        void evaluatingATargetedFlagForASegmentMemberReturnsTrueWithRuleMatchedReason() throws Exception {
            Fixture fixture = setUpEnvironment();
            UUID segmentId = createSegmentAs(fixture.token(), fixture.organizationId(), "VIP Users");
            addSegmentMemberAs(fixture.token(), segmentId, "vip-user-1");

            String key = uniqueFlagKey();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), key);
            enableFlagAs(fixture.token(), flagId);
            changeFlagTypeAs(fixture.token(), flagId, FlagType.TARGETED);
            addConditionRuleAs(fixture.token(), flagId, "segment", RuleOperator.EQUALS,
                    "\"" + segmentId + "\"", null, 0);

            EvaluateFlagRequest request = new EvaluateFlagRequest(key, "vip-user-1", null);

            mockMvc.perform(post("/api/environments/{environmentId}/evaluate", fixture.environmentId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.value").value(true))
                    .andExpect(jsonPath("$.reason").value("RULE_MATCHED"));
        }

        @Test
        void evaluatingATargetedFlagForANonMemberOfTheSegmentReturnsFalseWithNoRuleMatchedReason() throws Exception {
            Fixture fixture = setUpEnvironment();
            UUID segmentId = createSegmentAs(fixture.token(), fixture.organizationId(), "VIP Users");
            addSegmentMemberAs(fixture.token(), segmentId, "vip-user-1");

            String key = uniqueFlagKey();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), key);
            enableFlagAs(fixture.token(), flagId);
            changeFlagTypeAs(fixture.token(), flagId, FlagType.TARGETED);
            addConditionRuleAs(fixture.token(), flagId, "segment", RuleOperator.EQUALS,
                    "\"" + segmentId + "\"", null, 0);

            EvaluateFlagRequest request = new EvaluateFlagRequest(key, "someone-else", null);

            mockMvc.perform(post("/api/environments/{environmentId}/evaluate", fixture.environmentId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.value").value(false))
                    .andExpect(jsonPath("$.reason").value("NO_RULE_MATCHED"));
        }
    }
}