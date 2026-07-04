package com.prateek.featureflag.environment;

import com.prateek.featureflag.environment.dto.CreateEnvironmentRequest;
import com.prateek.featureflag.organization.MemberRole;
import com.prateek.featureflag.organization.OrganizationAuthorizationService;
import com.prateek.featureflag.project.Project;
import com.prateek.featureflag.project.ProjectService;
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
 * Environment creation, scoped under its owning project. Matches
 * {@code /api/projects/{projectId}/environments}.
 * <p>
 * Unlike {@code ProjectController}, the organization to authorize against
 * isn't in the URL — the project must be resolved first so its
 * {@code organization_id} can be reached via {@link Project#getOrganization()}
 * (a LAZY association; this works here because {@code spring.jpa.open-in-view}
 * is left at its Spring Boot default of {@code true} in this project, so the
 * Hibernate session is still open at this point in the request). This means
 * a {@code 404} (project not found) can occur before the {@code 403} role
 * check would even run — the reverse order from {@code OrganizationController}.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/environments")
public class EnvironmentController {

    private final EnvironmentService environmentService;
    private final ProjectService projectService;
    private final OrganizationAuthorizationService organizationAuthorizationService;

    public EnvironmentController(EnvironmentService environmentService,
                                  ProjectService projectService,
                                  OrganizationAuthorizationService organizationAuthorizationService) {
        this.environmentService = environmentService;
        this.projectService = projectService;
        this.organizationAuthorizationService = organizationAuthorizationService;
    }

    @PostMapping
    public ResponseEntity<EnvironmentResponse> create(@PathVariable UUID projectId,
                                                        @Valid @RequestBody CreateEnvironmentRequest request,
                                                        @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Project project = projectService.getActiveById(projectId);

            organizationAuthorizationService.requireRole(
                    project.getOrganization().getId(), principal.getUser().getId(),
                    MemberRole.OWNER, MemberRole.ADMIN);

            Environment environment = environmentService.create(project, request.name(), request.key());
            return ResponseEntity.status(HttpStatus.CREATED).body(EnvironmentResponse.from(environment));
        } catch (EntityNotFoundException projectNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException duplicateKey) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    public record EnvironmentResponse(UUID id, UUID projectId, String name, EnvironmentType key) {
        static EnvironmentResponse from(Environment environment) {
            return new EnvironmentResponse(
                    environment.getId(), environment.getProject().getId(), environment.getName(), environment.getKey());
        }
    }
}
