package com.prateek.featureflag.segment;

import com.prateek.featureflag.organization.MemberRole;
import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.organization.OrganizationAuthorizationService;
import com.prateek.featureflag.organization.OrganizationService;
import com.prateek.featureflag.security.CustomUserDetails;
import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice test for {@link SegmentController}, exercising real request
 * binding, {@code @Valid} validation, and {@link com.prateek.featureflag.common.GlobalExceptionHandler}
 * wiring end-to-end - not just the controller method body in isolation the
 * way the plain unit tests do.
 * <p>
 * <b>Assumptions/limitations, since this project's test setup (DB profile,
 * Testcontainers vs H2, etc.) isn't visible from this upload:</b> this uses
 * {@code @WebMvcTest} (web layer only, all service/repository beans mocked)
 * rather than a full {@code @SpringBootTest} against a real database, and
 * disables the servlet filter chain ({@code addFilters = false}) rather than
 * exercising the real JWT filter - {@code SecurityMockMvcRequestPostProcessors.authentication(...)}
 * injects the {@link CustomUserDetails} principal directly instead. This
 * requires {@code spring-boot-starter-security-test} on the test classpath
 * (Spring Boot 4's rename of the old {@code spring-security-test} dependency).
 * If you'd rather have a true end-to-end test through the real filter chain
 * and a real/test database, let me know your test DB setup and I'll convert
 * this to a full {@code @SpringBootTest}.
 */
@WebMvcTest(SegmentController.class)
@AutoConfigureMockMvc(addFilters = false)
class SegmentControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SegmentService segmentService;

    @MockitoBean
    private SegmentUserService segmentUserService;

    @MockitoBean
    private OrganizationService organizationService;

    @MockitoBean
    private OrganizationAuthorizationService organizationAuthorizationService;

    private Organization organization;
    private User user;
    private UsernamePasswordAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        organization = new Organization("Acme", "acme");
        setId(organization, UUID.randomUUID());
        user = new User("owner@example.com", "hash", "Owner");
        setId(user, UUID.randomUUID());
        CustomUserDetails principal = new CustomUserDetails(user);
        authToken = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void create_returns201_withLocationBodyMatchingService() throws Exception {
        Segment segment = new Segment(organization, "beta-testers");
        setId(segment, UUID.randomUUID());
        when(organizationService.getActiveById(organization.getId())).thenReturn(organization);
        when(segmentService.create(eq(organization), eq("beta-testers"), eq(user))).thenReturn(segment);

        mockMvc.perform(post("/api/organizations/{organizationId}/segments", organization.getId())
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "beta-testers"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("beta-testers"))
                .andExpect(jsonPath("$.organizationId").value(organization.getId().toString()));
    }

    @Test
    void create_returns400_whenNameIsBlank() throws Exception {
        // Exercises real @Valid / MethodArgumentNotValidException -> 400 wiring,
        // without ever reaching SegmentService.
        mockMvc.perform(post("/api/organizations/{organizationId}/segments", organization.getId())
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns403_whenCallerLacksManageRole() throws Exception {
        doThrow(new AccessDeniedException("not allowed"))
                .when(organizationAuthorizationService)
                .requireRole(eq(organization.getId()), eq(user.getId()), eq(MemberRole.OWNER), eq(MemberRole.ADMIN));

        mockMvc.perform(post("/api/organizations/{organizationId}/segments", organization.getId())
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "beta-testers"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_returns404_whenSegmentDoesNotExist() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(segmentService.getActiveById(missingId))
                .thenThrow(new EntityNotFoundException("Segment not found: " + missingId));

        mockMvc.perform(get("/api/segments/{segmentId}", missingId).with(authentication(authToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addMember_returns409_whenUserAlreadyAMember() throws Exception {
        Segment segment = new Segment(organization, "beta-testers");
        setId(segment, UUID.randomUUID());
        when(segmentService.getActiveById(segment.getId())).thenReturn(segment);
        when(segmentUserService.addMember(eq(segment), eq("user-123"), eq(user)))
                .thenThrow(new IllegalStateException("Already a member: user-123"));

        mockMvc.perform(post("/api/segments/{segmentId}/members", segment.getId())
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIdentifier": "user-123"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void removeMember_acceptsQueryParamIdentifierContainingSlash() throws Exception {
        // Regression test for the path-variable bug fixed in the previous batch:
        // this now goes through ?userIdentifier=... so slashes survive intact.
        Segment segment = new Segment(organization, "beta-testers");
        setId(segment, UUID.randomUUID());
        when(segmentService.getActiveById(segment.getId())).thenReturn(segment);

        mockMvc.perform(delete("/api/segments/{segmentId}/members", segment.getId())
                        .param("userIdentifier", "org/user-42")
                        .with(authentication(authToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void listMembers_returns200_withServiceResults() throws Exception {
        Segment segment = new Segment(organization, "beta-testers");
        setId(segment, UUID.randomUUID());
        SegmentUser member = new SegmentUser(segment, "user-123");
        when(segmentService.getActiveById(segment.getId())).thenReturn(segment);
        when(segmentUserService.listMembers(segment.getId())).thenReturn(List.of(member));

        mockMvc.perform(get("/api/segments/{segmentId}/members", segment.getId()).with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userIdentifier").value("user-123"));
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
