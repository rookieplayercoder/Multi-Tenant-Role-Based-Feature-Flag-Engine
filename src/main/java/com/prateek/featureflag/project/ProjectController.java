package com.prateek.featureflag.project;

import com.prateek.featureflag.organization.MemberRole;
import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.organization.OrganizationAuthorizationService;
import com.prateek.featureflag.organization.OrganizationService;
import com.prateek.featureflag.project.dto.CreateProjectRequest;
import com.prateek.featureflag.security.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Project creation, scoped under its owning organization. Matches
 * {@code /api/organizations/{organizationId}/projects}. Only {@code OWNER}
 * and {@code ADMIN} members of that organization may create a project,
 * enforced via the existing {@link OrganizationAuthorizationService}
 * (same guard used in {@code OrganizationController}).
 * <p>
 * Response is a small nested record, not the {@link Project} entity itself —
 * same reasoning as {@code OrganizationController}'s nested response records.
 */
@RestController
@RequestMapping("/api/organizations/{organizationId}/projects")
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

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@PathVariable UUID organizationId,
                                                  @Valid @RequestBody CreateProjectRequest request,
                                                  @AuthenticationPrincipal CustomUserDetails principal) {
        organizationAuthorizationService.requireRole(
                organizationId, principal.getUser().getId(), MemberRole.OWNER, MemberRole.ADMIN);

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

    public record ProjectResponse(UUID id, UUID organizationId, String name, String key) {
        static ProjectResponse from(Project project) {
            return new ProjectResponse(
                    project.getId(), project.getOrganization().getId(), project.getName(), project.getKey());
        }
    }
}
