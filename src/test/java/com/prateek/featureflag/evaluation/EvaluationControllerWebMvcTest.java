package com.prateek.featureflag.evaluation;

import com.prateek.featureflag.security.CustomUserDetailsService;
import com.prateek.featureflag.security.apikey.ApiKeyAuthenticationService;
import com.prateek.featureflag.security.jwt.JwtService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.prateek.featureflag.environment.Environment;
import com.prateek.featureflag.environment.EnvironmentService;
import com.prateek.featureflag.environment.EnvironmentType;
import com.prateek.featureflag.flag.FeatureFlag;
import com.prateek.featureflag.flag.FeatureFlagService;
import com.prateek.featureflag.organization.MemberRole;
import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.organization.OrganizationAuthorizationService;
import com.prateek.featureflag.project.Project;
import com.prateek.featureflag.security.CustomUserDetails;
import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice test for {@link EvaluationController} — arguably the single
 * most "critical" API flow in the whole app: a dashboard user testing how a
 * flag resolves for a given user/attributes. Same assumptions/limitations as
 * {@code SegmentControllerWebMvcTest} (see its Javadoc): slice test with
 * mocked services, filters disabled, principal injected directly.
 */

@WebMvcTest(EvaluationController.class)
@AutoConfigureMockMvc(addFilters = false)
class EvaluationControllerWebMvcTest {


    @MockitoBean
    private ApiKeyAuthenticationService apiKeyAuthenticationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeatureFlagService featureFlagService;

    @MockitoBean
    private FeatureFlagEvaluationService featureFlagEvaluationService;

    @MockitoBean
    private EnvironmentService environmentService;

    @MockitoBean
    private OrganizationAuthorizationService organizationAuthorizationService;

    private Environment environment;
    private User user;
    private UsernamePasswordAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        Organization organization = new Organization("Acme", "acme");
        setId(organization, UUID.randomUUID());
        Project project = new Project(organization, "Web", "web");
        setId(project, UUID.randomUUID());
        environment = new Environment(project, "Production", EnvironmentType.PROD);
        setId(environment, UUID.randomUUID());
        user = new User("dev@example.com", "hash", "Dev");
        setId(user, UUID.randomUUID());
        CustomUserDetails principal = new CustomUserDetails(user);
        authToken = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void evaluate_returns200_withResultFromEvaluationService() throws Exception {
        FeatureFlag flag = new FeatureFlag(environment, "checkout-v2", "Checkout V2", user);
        setId(flag, UUID.randomUUID());
        when(environmentService.getActiveById(environment.getId())).thenReturn(environment);
        when(featureFlagService.getActiveByEnvironmentAndKey(environment.getId(), "checkout-v2")).thenReturn(flag);
        when(featureFlagEvaluationService.evaluate(eq(flag), any()))
                .thenReturn(EvaluationResult.of(flag, true, EvaluationResult.Reason.RULE_MATCHED));

        mockMvc.perform(post("/api/environments/{environmentId}/evaluate", environment.getId())
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flagKey": "checkout-v2", "userIdentifier": "user-1", "attributes": {}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(true))
                .andExpect(jsonPath("$.reason").value("RULE_MATCHED"));
    }

    @Test
    void evaluate_returns404_whenFlagKeyDoesNotExistInEnvironment() throws Exception {
        when(environmentService.getActiveById(environment.getId())).thenReturn(environment);
        when(featureFlagService.getActiveByEnvironmentAndKey(environment.getId(), "missing-flag"))
                .thenThrow(new EntityNotFoundException("Feature flag not found"));

        mockMvc.perform(post("/api/environments/{environmentId}/evaluate", environment.getId())
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flagKey": "missing-flag"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void evaluate_returns400_whenFlagKeyIsBlank() throws Exception {
        mockMvc.perform(post("/api/environments/{environmentId}/evaluate", environment.getId())
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flagKey": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void evaluate_returns403_whenCallerIsNotOrgMember() throws Exception {
        when(environmentService.getActiveById(environment.getId())).thenReturn(environment);
        doThrow(new AccessDeniedException("not a member"))
                .when(organizationAuthorizationService)
                .requireRole(eq(environment.getProject().getOrganization().getId()), eq(user.getId()),
                        eq(MemberRole.OWNER), eq(MemberRole.ADMIN), eq(MemberRole.EDITOR));

        mockMvc.perform(post("/api/environments/{environmentId}/evaluate", environment.getId())
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flagKey": "checkout-v2"}
                                """))
                .andExpect(status().isForbidden());
    }

    private static void setId(Object entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
