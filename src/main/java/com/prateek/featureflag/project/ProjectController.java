package com.prateek.featureflag.project;

import com.prateek.featureflag.organization.MemberRole;
import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.organization.OrganizationAuthorizationService;
import com.prateek.featureflag.organization.OrganizationService;
import com.prateek.featureflag.project.dto.CreateProjectRequest;
import com.prateek.featureflag.project.dto.UpdateProjectRequest;
import com.prateek.featureflag.security.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Project CRUD. Two route groups, matching the convention established by
 * {@code FeatureFlagController}/{@code FeatureRuleController}/
 * {@code SegmentController}: organization-scoped
 * ({@code /api/organizations/{organizationId}/projects}) for create/list,
 * and project-scoped ({@code /api/projects/{projectId}}) for
 * get/rename/delete. No class-level {@code @RequestMapping} — the two
 * groups have different prefixes, so each method declares its full path,
 * same as {@code FeatureFlagController}.
 * <p>
 * Project-scoped endpoints resolve the owning organization via
 * {@link Project#getOrganization()} rather than trusting a URL path
 * segment — there's deliberately no {@code organizationId} in these
 * routes to go stale against the fetched entity's real parent, the same
 * reasoning already used by {@code SegmentController}'s segment-scoped
 * routes and {@code FeatureFlagController}'s flag-scoped ones.
 * <p>
 * Local try/catch is kept here (matching {@code FeatureFlagController}/
 * {@code SegmentController}, which still do the same) rather than
 * migrated onto {@code GlobalExceptionHandler} — that migration has so
 * far only been done for {@code OrganizationController}, and doing it
 * here too would be an unrelated change outside this batch's scope.
 * <p>
 * {@code create} requires {@code OWNER}/{@code ADMIN} (unchanged from
 * before). {@code rename}/{@code delete} use the same tier. {@code list}/
 * {@code getById} are open to any org member (all four roles), matching
 * how {@code OrganizationController.getById} and
 * {@code SegmentController.listByOrganization}/{@code getById} scope
 * their own reads.
 */
@RestController
public class ProjectController {

    private final ProjectService projectService;
    private final OrganizationService organizationService;
    private final OrganizationAuthorizationService organizationAuthorizationService;

    public ProjectController(ProjectService projectService,
                             OrganizationService organizationService,
                             OrganizationAuthorizationService organizationAuthorizationService) {
        this.projectService = projectService;
        this.organizationService = organizationService;
        this.organizationAuthorizationService = organizationAuthorizationService;
    }

    @PostMapping("/api/organizations/{organizationId}/projects")
    public ResponseEntity<ProjectResponse> create(@PathVariable UUID organizationId,
                                                  @Valid @RequestBody CreateProjectRequest request,
                                                  @AuthenticationPrincipal CustomUserDetails principal) {
        requireManageRole(organizationId, principal);

        try {
            Organization organization = organizationService.getActiveById(organizationId);
            Project project = projectService.create(organization, request.name(), request.key(), principal.getUser());
            return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(project));
        } catch (EntityNotFoundException organizationNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException duplicateKey) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/api/organizations/{organizationId}/projects")
    public ResponseEntity<List<ProjectResponse>> listByOrganization(@PathVariable UUID organizationId,
                                                                    @AuthenticationPrincipal CustomUserDetails principal) {
        requireMembership(organizationId, principal);

        List<ProjectResponse> projects = projectService.listActiveByOrganization(organizationId).stream()
                .map(ProjectResponse::from)
                .toList();
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/api/projects/{projectId}")
    public ResponseEntity<ProjectResponse> getById(@PathVariable UUID projectId,
                                                   @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Project project = projectService.getActiveById(projectId);
            requireMembership(project.getOrganization().getId(), principal);
            return ResponseEntity.ok(ProjectResponse.from(project));
        } catch (EntityNotFoundException projectNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/api/projects/{projectId}")
    public ResponseEntity<ProjectResponse> rename(@PathVariable UUID projectId,
                                                  @Valid @RequestBody UpdateProjectRequest request,
                                                  @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Project project = projectService.getActiveById(projectId);
            requireManageRole(project.getOrganization().getId(), principal);

            Project renamed = projectService.rename(projectId, request.name(), principal.getUser());
            return ResponseEntity.ok(ProjectResponse.from(renamed));
        } catch (EntityNotFoundException projectNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @DeleteMapping("/api/projects/{projectId}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Project project = projectService.getActiveById(projectId);
            requireManageRole(project.getOrganization().getId(), principal);

            projectService.softDelete(projectId, principal.getUser());
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException projectNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private void requireManageRole(UUID organizationId, CustomUserDetails principal) {
        organizationAuthorizationService.requireRole(
                organizationId, principal.getUser().getId(), MemberRole.OWNER, MemberRole.ADMIN);
    }

    private void requireMembership(UUID organizationId, CustomUserDetails principal) {
        organizationAuthorizationService.requireRole(
                organizationId, principal.getUser().getId(),
                MemberRole.OWNER, MemberRole.ADMIN, MemberRole.EDITOR, MemberRole.VIEWER);
    }

    public record ProjectResponse(UUID id, UUID organizationId, String name, String key) {
        static ProjectResponse from(Project project) {
            return new ProjectResponse(
                    project.getId(), project.getOrganization().getId(), project.getName(), project.getKey());
        }
    }
}