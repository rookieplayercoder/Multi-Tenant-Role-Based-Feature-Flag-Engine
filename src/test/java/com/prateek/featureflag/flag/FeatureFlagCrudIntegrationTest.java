package com.prateek.featureflag.flag;

import com.prateek.featureflag.auth.dto.LoginResponse;
import com.prateek.featureflag.auth.dto.RegisterRequest;
import com.prateek.featureflag.environment.EnvironmentType;
import com.prateek.featureflag.environment.dto.CreateEnvironmentRequest;
import com.prateek.featureflag.flag.dto.ChangeFlagTypeRequest;
import com.prateek.featureflag.flag.dto.CreateFeatureFlagRequest;
import com.prateek.featureflag.flag.dto.UpdateFeatureFlagRequest;
import com.prateek.featureflag.organization.dto.CreateOrganizationRequest;
import com.prateek.featureflag.project.dto.CreateProjectRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code /api/environments/{environmentId}/flags}
 * (create/list) and {@code /api/flags/{flagId}} + its
 * {@code /enable}/{@code /disable}/{@code /type} sub-resources. There is
 * no delete endpoint in {@code FeatureFlagController} — see accompanying
 * chat message.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class FeatureFlagCrudIntegrationTest {

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
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }

    private UUID createProjectAs(String token, UUID organizationId) throws Exception {
        CreateProjectRequest request = new CreateProjectRequest("Web App", uniqueProjectKey());
        MvcResult result = mockMvc.perform(post("/api/organizations/{organizationId}/projects", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }

    private UUID createEnvironmentAs(String token, UUID projectId) throws Exception {
        CreateEnvironmentRequest request = new CreateEnvironmentRequest("Production", EnvironmentType.PROD);
        MvcResult result = mockMvc.perform(post("/api/projects/{projectId}/environments", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }

    /** Registers a fresh owner and builds an org/project/environment chain for it, returning both the token and environment ID. */
    private record Fixture(String token, UUID environmentId) {
    }

    private Fixture setUpEnvironment() throws Exception {
        String token = registerNewUser();
        UUID organizationId = createOrganizationAs(token);
        UUID projectId = createProjectAs(token, organizationId);
        UUID environmentId = createEnvironmentAs(token, projectId);
        return new Fixture(token, environmentId);
    }

    private UUID createFlagAs(String token, UUID environmentId, String key, String name, String description) throws Exception {
        CreateFeatureFlagRequest request = new CreateFeatureFlagRequest(key, name, description);
        MvcResult result = mockMvc.perform(post("/api/environments/{environmentId}/flags", environmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }

    @Nested
    class Create {

        @Test
        void creatingFeatureFlagAsEditorReturnsCreatedAsBooleanTypeWithVersionOne() throws Exception {
            Fixture fixture = setUpEnvironment();
            CreateFeatureFlagRequest request = new CreateFeatureFlagRequest(uniqueFlagKey(), "Checkout Flow", null);

            mockMvc.perform(post("/api/environments/{environmentId}/flags", fixture.environmentId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.environmentId").value(fixture.environmentId().toString()))
                    .andExpect(jsonPath("$.key").value(request.key()))
                    .andExpect(jsonPath("$.name").value("Checkout Flow"))
                    .andExpect(jsonPath("$.enabled").value(false))
                    .andExpect(jsonPath("$.flagType").value("BOOLEAN"))
                    .andExpect(jsonPath("$.version").value(1));
        }

        @Test
        void creatingFeatureFlagWithADescriptionAppliesItAndBumpsVersionToTwo() throws Exception {
            Fixture fixture = setUpEnvironment();
            CreateFeatureFlagRequest request = new CreateFeatureFlagRequest(
                    uniqueFlagKey(), "Checkout Flow", "Controls the new checkout experience");

            mockMvc.perform(post("/api/environments/{environmentId}/flags", fixture.environmentId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.description").value("Controls the new checkout experience"))
                    .andExpect(jsonPath("$.version").value(2));
        }

        @Test
        void creatingFeatureFlagWithAnAlreadyUsedKeyInTheSameEnvironmentReturnsConflict() throws Exception {
            Fixture fixture = setUpEnvironment();
            String key = uniqueFlagKey();
            createFlagAs(fixture.token(), fixture.environmentId(), key, "First Flag", null);
            CreateFeatureFlagRequest duplicateRequest = new CreateFeatureFlagRequest(key, "Second Flag", null);

            mockMvc.perform(post("/api/environments/{environmentId}/flags", fixture.environmentId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(duplicateRequest)))
                    .andExpect(status().isConflict());
        }

        @Test
        void creatingFeatureFlagInANonexistentEnvironmentReturnsNotFound() throws Exception {
            String token = registerNewUser();
            CreateFeatureFlagRequest request = new CreateFeatureFlagRequest(uniqueFlagKey(), "Checkout Flow", null);

            mockMvc.perform(post("/api/environments/{environmentId}/flags", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void creatingFeatureFlagAsANonMemberOfTheEnvironmentsOrganizationReturnsForbidden() throws Exception {
            Fixture fixture = setUpEnvironment();
            String outsiderToken = registerNewUser();
            CreateFeatureFlagRequest request = new CreateFeatureFlagRequest(uniqueFlagKey(), "Checkout Flow", null);

            mockMvc.perform(post("/api/environments/{environmentId}/flags", fixture.environmentId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void creatingFeatureFlagWithAnInvalidKeyFormatReturnsBadRequest() throws Exception {
            Fixture fixture = setUpEnvironment();
            CreateFeatureFlagRequest request = new CreateFeatureFlagRequest("Not A Valid Key!", "Checkout Flow", null);

            mockMvc.perform(post("/api/environments/{environmentId}/flags", fixture.environmentId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Get {

        @Test
        void gettingFeatureFlagAsAnAuthorizedMemberReturnsOk() throws Exception {
            Fixture fixture = setUpEnvironment();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), uniqueFlagKey(), "Checkout Flow", null);

            mockMvc.perform(get("/api/flags/{flagId}", flagId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(flagId.toString()))
                    .andExpect(jsonPath("$.name").value("Checkout Flow"));
        }

        @Test
        void gettingANonexistentFeatureFlagReturnsNotFound() throws Exception {
            String token = registerNewUser();

            mockMvc.perform(get("/api/flags/{flagId}", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound());
        }

        @Test
        void gettingFeatureFlagAsANonMemberOfItsOrganizationReturnsForbidden() throws Exception {
            Fixture fixture = setUpEnvironment();
            String outsiderToken = registerNewUser();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), uniqueFlagKey(), "Checkout Flow", null);

            mockMvc.perform(get("/api/flags/{flagId}", flagId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Update {

        @Test
        void updatingFeatureFlagDetailsReturnsOkWithBumpedVersion() throws Exception {
            Fixture fixture = setUpEnvironment();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), uniqueFlagKey(), "Checkout Flow", null);
            UpdateFeatureFlagRequest request = new UpdateFeatureFlagRequest("Checkout Flow v2", "Updated description");

            mockMvc.perform(put("/api/flags/{flagId}", flagId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Checkout Flow v2"))
                    .andExpect(jsonPath("$.description").value("Updated description"))
                    .andExpect(jsonPath("$.version").value(2));
        }

        @Test
        void updatingANonexistentFeatureFlagReturnsNotFound() throws Exception {
            String token = registerNewUser();
            UpdateFeatureFlagRequest request = new UpdateFeatureFlagRequest("New Name", null);

            mockMvc.perform(put("/api/flags/{flagId}", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void updatingFeatureFlagAsANonMemberOfItsOrganizationReturnsForbidden() throws Exception {
            Fixture fixture = setUpEnvironment();
            String outsiderToken = registerNewUser();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), uniqueFlagKey(), "Checkout Flow", null);
            UpdateFeatureFlagRequest request = new UpdateFeatureFlagRequest("Hijacked Name", null);

            mockMvc.perform(put("/api/flags/{flagId}", flagId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class EnableDisable {

        @Test
        void enablingFeatureFlagReturnsOkWithEnabledTrue() throws Exception {
            Fixture fixture = setUpEnvironment();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), uniqueFlagKey(), "Checkout Flow", null);

            mockMvc.perform(post("/api/flags/{flagId}/enable", flagId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.version").value(2));
        }

        @Test
        void disablingAPreviouslyEnabledFeatureFlagReturnsOkWithEnabledFalse() throws Exception {
            Fixture fixture = setUpEnvironment();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), uniqueFlagKey(), "Checkout Flow", null);
            mockMvc.perform(post("/api/flags/{flagId}/enable", flagId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token()))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/flags/{flagId}/disable", flagId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false))
                    .andExpect(jsonPath("$.version").value(3));
        }

        @Test
        void enablingANonexistentFeatureFlagReturnsNotFound() throws Exception {
            String token = registerNewUser();

            mockMvc.perform(post("/api/flags/{flagId}/enable", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound());
        }

        @Test
        void enablingFeatureFlagAsANonMemberOfItsOrganizationReturnsForbidden() throws Exception {
            Fixture fixture = setUpEnvironment();
            String outsiderToken = registerNewUser();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), uniqueFlagKey(), "Checkout Flow", null);

            mockMvc.perform(post("/api/flags/{flagId}/enable", flagId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class ChangeType {

        @Test
        void changingFlagTypeToPercentageReturnsOkWithUpdatedType() throws Exception {
            Fixture fixture = setUpEnvironment();
            UUID flagId = createFlagAs(fixture.token(), fixture.environmentId(), uniqueFlagKey(), "Checkout Flow", null);
            ChangeFlagTypeRequest request = new ChangeFlagTypeRequest(FlagType.PERCENTAGE);

            mockMvc.perform(put("/api/flags/{flagId}/type", flagId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.flagType").value("PERCENTAGE"))
                    .andExpect(jsonPath("$.version").value(2));
        }

        @Test
        void changingTypeOfANonexistentFeatureFlagReturnsNotFound() throws Exception {
            String token = registerNewUser();
            ChangeFlagTypeRequest request = new ChangeFlagTypeRequest(FlagType.PERCENTAGE);

            mockMvc.perform(put("/api/flags/{flagId}/type", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Unauthorized {

        @Test
        void creatingFeatureFlagWithoutAuthenticationReturnsUnauthorized() throws Exception {
            CreateFeatureFlagRequest request = new CreateFeatureFlagRequest(uniqueFlagKey(), "Checkout Flow", null);

            mockMvc.perform(post("/api/environments/{environmentId}/flags", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void gettingFeatureFlagWithoutAuthenticationReturnsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/flags/{flagId}", UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void updatingFeatureFlagWithoutAuthenticationReturnsUnauthorized() throws Exception {
            UpdateFeatureFlagRequest request = new UpdateFeatureFlagRequest("New Name", null);

            mockMvc.perform(put("/api/flags/{flagId}", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void enablingFeatureFlagWithoutAuthenticationReturnsUnauthorized() throws Exception {
            mockMvc.perform(post("/api/flags/{flagId}/enable", UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void changingTypeWithoutAuthenticationReturnsUnauthorized() throws Exception {
            ChangeFlagTypeRequest request = new ChangeFlagTypeRequest(FlagType.PERCENTAGE);

            mockMvc.perform(put("/api/flags/{flagId}/type", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }
}