package com.prateek.featureflag.project;

import com.prateek.featureflag.auth.dto.LoginResponse;
import com.prateek.featureflag.auth.dto.RegisterRequest;
import com.prateek.featureflag.organization.dto.CreateOrganizationRequest;
import com.prateek.featureflag.project.dto.CreateProjectRequest;
import com.prateek.featureflag.project.dto.UpdateProjectRequest;
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
 * Integration tests for {@code /api/organizations/{organizationId}/projects}
 * (create/list) and {@code /api/projects/{projectId}} (get/rename/delete).
 * See class-level note in the accompanying chat message on why
 * existence-vs-authorization ordering differs by endpoint here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class ProjectCrudIntegrationTest {

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

    private UUID createProjectAs(String token, UUID organizationId, String name, String key) throws Exception {
        CreateProjectRequest request = new CreateProjectRequest(name, key);
        MvcResult result = mockMvc.perform(post("/api/organizations/{organizationId}/projects", organizationId)
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
        void creatingProjectAsOrganizationOwnerReturnsCreated() throws Exception {
            String token = registerNewUser();
            UUID organizationId = createOrganizationAs(token);
            CreateProjectRequest request = new CreateProjectRequest("Web App", uniqueKey());

            mockMvc.perform(post("/api/organizations/{organizationId}/projects", organizationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.organizationId").value(organizationId.toString()))
                    .andExpect(jsonPath("$.name").value("Web App"))
                    .andExpect(jsonPath("$.key").value(request.key()));
        }

        @Test
        void creatingProjectWithAnAlreadyUsedKeyInTheSameOrganizationReturnsConflict() throws Exception {
            String token = registerNewUser();
            UUID organizationId = createOrganizationAs(token);
            String key = uniqueKey();
            createProjectAs(token, organizationId, "First Project", key);
            CreateProjectRequest duplicateRequest = new CreateProjectRequest("Second Project", key);

            mockMvc.perform(post("/api/organizations/{organizationId}/projects", organizationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(duplicateRequest)))
                    .andExpect(status().isConflict());
        }

        @Test
        void creatingProjectInANonexistentOrganizationReturnsForbidden() throws Exception {
            // Authorization is checked before organization existence here too,
            // so a random organizationId yields 403, not 404.
            String token = registerNewUser();
            CreateProjectRequest request = new CreateProjectRequest("Web App", uniqueKey());

            mockMvc.perform(post("/api/organizations/{organizationId}/projects", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void creatingProjectAsANonMemberOfTheOrganizationReturnsForbidden() throws Exception {
            String ownerToken = registerNewUser();
            String outsiderToken = registerNewUser();
            UUID organizationId = createOrganizationAs(ownerToken);
            CreateProjectRequest request = new CreateProjectRequest("Web App", uniqueKey());

            mockMvc.perform(post("/api/organizations/{organizationId}/projects", organizationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void creatingProjectWithAnInvalidKeyFormatReturnsBadRequest() throws Exception {
            String token = registerNewUser();
            UUID organizationId = createOrganizationAs(token);
            CreateProjectRequest request = new CreateProjectRequest("Web App", "Not A Valid Key!");

            mockMvc.perform(post("/api/organizations/{organizationId}/projects", organizationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Get {

        @Test
        void gettingProjectAsAnOrganizationMemberReturnsOk() throws Exception {
            String token = registerNewUser();
            UUID organizationId = createOrganizationAs(token);
            UUID projectId = createProjectAs(token, organizationId, "Web App", uniqueKey());

            mockMvc.perform(get("/api/projects/{projectId}", projectId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(projectId.toString()))
                    .andExpect(jsonPath("$.name").value("Web App"));
        }

        @Test
        void gettingANonexistentProjectReturnsNotFound() throws Exception {
            // Existence is checked before authorization here, so this is a
            // genuine 404, unlike the organization-scoped create/list endpoints.
            String token = registerNewUser();

            mockMvc.perform(get("/api/projects/{projectId}", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound());
        }

        @Test
        void gettingProjectAsANonMemberOfItsOrganizationReturnsForbidden() throws Exception {
            String ownerToken = registerNewUser();
            String outsiderToken = registerNewUser();
            UUID organizationId = createOrganizationAs(ownerToken);
            UUID projectId = createProjectAs(ownerToken, organizationId, "Web App", uniqueKey());

            mockMvc.perform(get("/api/projects/{projectId}", projectId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Update {

        @Test
        void renamingProjectAsOrganizationOwnerReturnsOkWithUpdatedName() throws Exception {
            String token = registerNewUser();
            UUID organizationId = createOrganizationAs(token);
            UUID projectId = createProjectAs(token, organizationId, "Web App", uniqueKey());
            UpdateProjectRequest request = new UpdateProjectRequest("Web Application");

            mockMvc.perform(put("/api/projects/{projectId}", projectId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Web Application"));
        }

        @Test
        void renamingANonexistentProjectReturnsNotFound() throws Exception {
            String token = registerNewUser();
            UpdateProjectRequest request = new UpdateProjectRequest("New Name");

            mockMvc.perform(put("/api/projects/{projectId}", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void renamingProjectAsANonMemberOfItsOrganizationReturnsForbidden() throws Exception {
            String ownerToken = registerNewUser();
            String outsiderToken = registerNewUser();
            UUID organizationId = createOrganizationAs(ownerToken);
            UUID projectId = createProjectAs(ownerToken, organizationId, "Web App", uniqueKey());
            UpdateProjectRequest request = new UpdateProjectRequest("Hijacked Name");

            mockMvc.perform(put("/api/projects/{projectId}", projectId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Delete {

        @Test
        void deletingProjectAsOrganizationOwnerReturnsNoContent() throws Exception {
            String token = registerNewUser();
            UUID organizationId = createOrganizationAs(token);
            UUID projectId = createProjectAs(token, organizationId, "Web App", uniqueKey());

            mockMvc.perform(delete("/api/projects/{projectId}", projectId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNoContent());
        }

        @Test
        void deletingANonexistentProjectReturnsNotFound() throws Exception {
            String token = registerNewUser();

            mockMvc.perform(delete("/api/projects/{projectId}", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound());
        }

        @Test
        void deletingProjectAsANonMemberOfItsOrganizationReturnsForbidden() throws Exception {
            String ownerToken = registerNewUser();
            String outsiderToken = registerNewUser();
            UUID organizationId = createOrganizationAs(ownerToken);
            UUID projectId = createProjectAs(ownerToken, organizationId, "Web App", uniqueKey());

            mockMvc.perform(delete("/api/projects/{projectId}", projectId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Unauthorized {

        @Test
        void creatingProjectWithoutAuthenticationReturnsUnauthorized() throws Exception {
            CreateProjectRequest request = new CreateProjectRequest("Web App", uniqueKey());

            mockMvc.perform(post("/api/organizations/{organizationId}/projects", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void gettingProjectWithoutAuthenticationReturnsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/projects/{projectId}", UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void renamingProjectWithoutAuthenticationReturnsUnauthorized() throws Exception {
            UpdateProjectRequest request = new UpdateProjectRequest("New Name");

            mockMvc.perform(put("/api/projects/{projectId}", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void deletingProjectWithoutAuthenticationReturnsUnauthorized() throws Exception {
            mockMvc.perform(delete("/api/projects/{projectId}", UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }
    }
}