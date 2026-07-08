package com.prateek.featureflag.environment;

import com.prateek.featureflag.auth.dto.LoginResponse;
import com.prateek.featureflag.auth.dto.RegisterRequest;
import com.prateek.featureflag.environment.dto.CreateEnvironmentRequest;
import com.prateek.featureflag.environment.dto.UpdateEnvironmentRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code /api/projects/{projectId}/environments}
 * (create/list) and {@code /api/environments/{environmentId}}
 * (get/rename/delete). See class-level note in the accompanying chat
 * message on the existence-vs-authorization ordering here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class EnvironmentCrudIntegrationTest {

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

    private static String uniqueKey() {
        return "proj-" + UUID.randomUUID();
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
        CreateProjectRequest request = new CreateProjectRequest("Web App", uniqueKey());
        MvcResult result = mockMvc.perform(post("/api/organizations/{organizationId}/projects", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }

    private UUID createEnvironmentAs(String token, UUID projectId, String name, EnvironmentType key) throws Exception {
        CreateEnvironmentRequest request = new CreateEnvironmentRequest(name, key);
        MvcResult result = mockMvc.perform(post("/api/projects/{projectId}/environments", projectId)
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
        void creatingEnvironmentAsOrganizationOwnerReturnsCreated() throws Exception {
            String token = registerNewUser();
            UUID organizationId = createOrganizationAs(token);
            UUID projectId = createProjectAs(token, organizationId);
            CreateEnvironmentRequest request = new CreateEnvironmentRequest("Development", EnvironmentType.DEV);

            mockMvc.perform(post("/api/projects/{projectId}/environments", projectId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                    .andExpect(jsonPath("$.name").value("Development"))
                    .andExpect(jsonPath("$.key").value("DEV"));
        }

        @Test
        void creatingEnvironmentWithAnAlreadyUsedKeyInTheSameProjectReturnsConflict() throws Exception {
            String token = registerNewUser();
            UUID organizationId = createOrganizationAs(token);
            UUID projectId = createProjectAs(token, organizationId);
            createEnvironmentAs(token, projectId, "Development", EnvironmentType.DEV);
            CreateEnvironmentRequest duplicateRequest = new CreateEnvironmentRequest("Dev Again", EnvironmentType.DEV);

            mockMvc.perform(post("/api/projects/{projectId}/environments", projectId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(duplicateRequest)))
                    .andExpect(status().isConflict());
        }

        @Test
        void creatingEnvironmentInANonexistentProjectReturnsNotFound() throws Exception {
            // Project existence is checked before authorization here, so
            // this is a genuine 404 — unlike ProjectController.create's
            // equivalent nonexistent-organization case, which returns 403.
            String token = registerNewUser();
            CreateEnvironmentRequest request = new CreateEnvironmentRequest("Development", EnvironmentType.DEV);

            mockMvc.perform(post("/api/projects/{projectId}/environments", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void creatingEnvironmentAsANonMemberOfTheProjectsOrganizationReturnsForbidden() throws Exception {
            String ownerToken = registerNewUser();
            String outsiderToken = registerNewUser();
            UUID organizationId = createOrganizationAs(ownerToken);
            UUID projectId = createProjectAs(ownerToken, organizationId);
            CreateEnvironmentRequest request = new CreateEnvironmentRequest("Development", EnvironmentType.DEV);

            mockMvc.perform(post("/api/projects/{projectId}/environments", projectId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void creatingEnvironmentWithABlankNameReturnsBadRequest() throws Exception {
            String token = registerNewUser();
            UUID organizationId = createOrganizationAs(token);
            UUID projectId = createProjectAs(token, organizationId);
            CreateEnvironmentRequest request = new CreateEnvironmentRequest("", EnvironmentType.DEV);

            mockMvc.perform(post("/api/projects/{projectId}/environments", projectId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Get {

        @Test
        void gettingEnvironmentAsAnOrganizationMemberReturnsOk() throws Exception {
            String token = registerNewUser();
            UUID organizationId = createOrganizationAs(token);
            UUID projectId = createProjectAs(token, organizationId);
            UUID environmentId = createEnvironmentAs(token, projectId, "Development", EnvironmentType.DEV);

            mockMvc.perform(get("/api/environments/{environmentId}", environmentId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(environmentId.toString()))
                    .andExpect(jsonPath("$.name").value("Development"))
                    .andExpect(jsonPath("$.key").value("DEV"));
        }

        @Test
        void gettingANonexistentEnvironmentReturnsNotFound() throws Exception {
            String token = registerNewUser();

            mockMvc.perform(get("/api/environments/{environmentId}", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound());
        }

        @Test
        void gettingEnvironmentAsANonMemberOfItsOrganizationReturnsForbidden() throws Exception {
            String ownerToken = registerNewUser();
            String outsiderToken = registerNewUser();
            UUID organizationId = createOrganizationAs(ownerToken);
            UUID projectId = createProjectAs(ownerToken, organizationId);
            UUID environmentId = createEnvironmentAs(ownerToken, projectId, "Development", EnvironmentType.DEV);

            mockMvc.perform(get("/api/environments/{environmentId}", environmentId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Update {

        @Test
        void renamingEnvironmentAsOrganizationOwnerReturnsOkWithUpdatedName() throws Exception {
            String token = registerNewUser();
            UUID organizationId = createOrganizationAs(token);
            UUID projectId = createProjectAs(token, organizationId);
            UUID environmentId = createEnvironmentAs(token, projectId, "Development", EnvironmentType.DEV);
            UpdateEnvironmentRequest request = new UpdateEnvironmentRequest("Dev Environment");

            mockMvc.perform(put("/api/environments/{environmentId}", environmentId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Dev Environment"));
        }

        @Test
        void renamingANonexistentEnvironmentReturnsNotFound() throws Exception {
            String token = registerNewUser();
            UpdateEnvironmentRequest request = new UpdateEnvironmentRequest("New Name");

            mockMvc.perform(put("/api/environments/{environmentId}", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void renamingEnvironmentAsANonMemberOfItsOrganizationReturnsForbidden() throws Exception {
            String ownerToken = registerNewUser();
            String outsiderToken = registerNewUser();
            UUID organizationId = createOrganizationAs(ownerToken);
            UUID projectId = createProjectAs(ownerToken, organizationId);
            UUID environmentId = createEnvironmentAs(ownerToken, projectId, "Development", EnvironmentType.DEV);
            UpdateEnvironmentRequest request = new UpdateEnvironmentRequest("Hijacked Name");

            mockMvc.perform(put("/api/environments/{environmentId}", environmentId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Delete {

        @Test
        void deletingEnvironmentAsOrganizationOwnerReturnsNoContent() throws Exception {
            String token = registerNewUser();
            UUID organizationId = createOrganizationAs(token);
            UUID projectId = createProjectAs(token, organizationId);
            UUID environmentId = createEnvironmentAs(token, projectId, "Development", EnvironmentType.DEV);

            mockMvc.perform(delete("/api/environments/{environmentId}", environmentId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNoContent());
        }

        @Test
        void deletingANonexistentEnvironmentReturnsNotFound() throws Exception {
            String token = registerNewUser();

            mockMvc.perform(delete("/api/environments/{environmentId}", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound());
        }

        @Test
        void deletingEnvironmentAsANonMemberOfItsOrganizationReturnsForbidden() throws Exception {
            String ownerToken = registerNewUser();
            String outsiderToken = registerNewUser();
            UUID organizationId = createOrganizationAs(ownerToken);
            UUID projectId = createProjectAs(ownerToken, organizationId);
            UUID environmentId = createEnvironmentAs(ownerToken, projectId, "Development", EnvironmentType.DEV);

            mockMvc.perform(delete("/api/environments/{environmentId}", environmentId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Unauthorized {

        @Test
        void creatingEnvironmentWithoutAuthenticationReturnsUnauthorized() throws Exception {
            CreateEnvironmentRequest request = new CreateEnvironmentRequest("Development", EnvironmentType.DEV);

            mockMvc.perform(post("/api/projects/{projectId}/environments", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void gettingEnvironmentWithoutAuthenticationReturnsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/environments/{environmentId}", UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void renamingEnvironmentWithoutAuthenticationReturnsUnauthorized() throws Exception {
            UpdateEnvironmentRequest request = new UpdateEnvironmentRequest("New Name");

            mockMvc.perform(put("/api/environments/{environmentId}", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void deletingEnvironmentWithoutAuthenticationReturnsUnauthorized() throws Exception {
            mockMvc.perform(delete("/api/environments/{environmentId}", UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }
    }
}