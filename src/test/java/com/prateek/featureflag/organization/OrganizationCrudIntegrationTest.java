package com.prateek.featureflag.organization;

import com.prateek.featureflag.auth.dto.LoginResponse;
import com.prateek.featureflag.auth.dto.RegisterRequest;
import com.prateek.featureflag.organization.dto.CreateOrganizationRequest;
import com.prateek.featureflag.organization.dto.UpdateOrganizationRequest;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code /api/organizations/**}. Uses real
 * registration/login through {@code /api/auth/**} to obtain JWTs rather
 * than mocking authentication, so the full filter chain
 * (JWT parsing -> {@code CustomUserDetailsService} -> membership
 * authorization) is exercised exactly as it runs in production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class OrganizationCrudIntegrationTest {

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

    /** Registers a brand-new user and returns their access token. */
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

    /** Creates an organization as the given caller (who becomes its OWNER) and returns its ID. */
    private UUID createOrganizationAs(String token, String name, String slug) throws Exception {
        CreateOrganizationRequest request = new CreateOrganizationRequest(name, slug);
        MvcResult result = mockMvc.perform(post("/api/organizations")
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
        void creatingOrganizationWithValidPayloadReturnsCreatedWithOwnerMembership() throws Exception {
            String token = registerNewUser();
            CreateOrganizationRequest request = new CreateOrganizationRequest("Acme Inc", uniqueSlug());

            mockMvc.perform(post("/api/organizations")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.name").value("Acme Inc"))
                    .andExpect(jsonPath("$.slug").value(request.slug()));
        }

        @Test
        void creatingOrganizationWithAnAlreadyUsedSlugReturnsConflict() throws Exception {
            String token = registerNewUser();
            String slug = uniqueSlug();
            createOrganizationAs(token, "First Org", slug);
            CreateOrganizationRequest duplicateRequest = new CreateOrganizationRequest("Second Org", slug);

            mockMvc.perform(post("/api/organizations")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(duplicateRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        void creatingOrganizationWithABlankNameReturnsBadRequest() throws Exception {
            String token = registerNewUser();
            CreateOrganizationRequest request = new CreateOrganizationRequest("", uniqueSlug());

            mockMvc.perform(post("/api/organizations")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void creatingOrganizationWithAnInvalidSlugFormatReturnsBadRequest() throws Exception {
            String token = registerNewUser();
            CreateOrganizationRequest request = new CreateOrganizationRequest("Acme Inc", "Not A Valid Slug!");

            mockMvc.perform(post("/api/organizations")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Get {

        @Test
        void gettingOrganizationAsAMemberReturnsOk() throws Exception {
            String token = registerNewUser();
            UUID organizationId = createOrganizationAs(token, "Acme Inc", uniqueSlug());

            mockMvc.perform(get("/api/organizations/{organizationId}", organizationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(organizationId.toString()))
                    .andExpect(jsonPath("$.name").value("Acme Inc"));
        }

        @Test
        void gettingOrganizationWithNoMembershipReturnsForbidden() throws Exception {
            // Authorization is checked before existence, so even a random,
            // never-created organization ID surfaces as 403, not 404 —
            // see class-level Javadoc.
            String token = registerNewUser();

            mockMvc.perform(get("/api/organizations/{organizationId}", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        @Test
        void gettingOrganizationAsANonMemberReturnsForbidden() throws Exception {
            String ownerToken = registerNewUser();
            String outsiderToken = registerNewUser();
            UUID organizationId = createOrganizationAs(ownerToken, "Acme Inc", uniqueSlug());

            mockMvc.perform(get("/api/organizations/{organizationId}", organizationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        void gettingASoftDeletedOrganizationAsAFormerMemberReturnsNotFound() throws Exception {
            String token = registerNewUser();
            UUID organizationId = createOrganizationAs(token, "Acme Inc", uniqueSlug());
            mockMvc.perform(delete("/api/organizations/{organizationId}", organizationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNoContent());

            // The caller's Member row still exists (membership has no soft
            // delete), so authorization passes; the 404 comes from
            // OrganizationService.getActiveById filtering out the
            // now-deleted organization.
            mockMvc.perform(get("/api/organizations/{organizationId}", organizationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Update {

        @Test
        void renamingOrganizationAsOwnerReturnsOkWithUpdatedName() throws Exception {
            String token = registerNewUser();
            UUID organizationId = createOrganizationAs(token, "Acme Inc", uniqueSlug());
            UpdateOrganizationRequest request = new UpdateOrganizationRequest("Acme Corporation");

            mockMvc.perform(put("/api/organizations/{organizationId}", organizationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Acme Corporation"));
        }

        @Test
        void renamingOrganizationAsANonMemberReturnsForbidden() throws Exception {
            String ownerToken = registerNewUser();
            String outsiderToken = registerNewUser();
            UUID organizationId = createOrganizationAs(ownerToken, "Acme Inc", uniqueSlug());
            UpdateOrganizationRequest request = new UpdateOrganizationRequest("Hijacked Name");

            mockMvc.perform(put("/api/organizations/{organizationId}", organizationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void renamingOrganizationWithABlankNameReturnsBadRequest() throws Exception {
            String token = registerNewUser();
            UUID organizationId = createOrganizationAs(token, "Acme Inc", uniqueSlug());
            UpdateOrganizationRequest request = new UpdateOrganizationRequest("");

            mockMvc.perform(put("/api/organizations/{organizationId}", organizationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Delete {

        @Test
        void deletingOrganizationAsOwnerReturnsNoContent() throws Exception {
            String token = registerNewUser();
            UUID organizationId = createOrganizationAs(token, "Acme Inc", uniqueSlug());

            mockMvc.perform(delete("/api/organizations/{organizationId}", organizationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNoContent());
        }

        @Test
        void deletingOrganizationAsANonMemberReturnsForbidden() throws Exception {
            String ownerToken = registerNewUser();
            String outsiderToken = registerNewUser();
            UUID organizationId = createOrganizationAs(ownerToken, "Acme Inc", uniqueSlug());

            mockMvc.perform(delete("/api/organizations/{organizationId}", organizationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Unauthorized {

        @Test
        void creatingOrganizationWithoutAuthenticationReturnsUnauthorized() throws Exception {
            CreateOrganizationRequest request = new CreateOrganizationRequest("Acme Inc", uniqueSlug());

            mockMvc.perform(post("/api/organizations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void gettingOrganizationWithoutAuthenticationReturnsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/organizations/{organizationId}", UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void renamingOrganizationWithoutAuthenticationReturnsUnauthorized() throws Exception {
            UpdateOrganizationRequest request = new UpdateOrganizationRequest("New Name");

            mockMvc.perform(put("/api/organizations/{organizationId}", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void deletingOrganizationWithoutAuthenticationReturnsUnauthorized() throws Exception {
            mockMvc.perform(delete("/api/organizations/{organizationId}", UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void requestWithAMalformedBearerTokenReturnsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/organizations/{organizationId}", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt"))
                    .andExpect(status().isUnauthorized());
        }
    }
}