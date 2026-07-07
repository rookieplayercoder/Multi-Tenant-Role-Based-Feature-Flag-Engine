package com.prateek.featureflag.environment;

import com.prateek.featureflag.environment.dto.CreateEnvironmentRequest;
import com.prateek.featureflag.environment.dto.UpdateEnvironmentRequest;
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
 * Environment CRUD. Two route groups, same convention as
 * {@code ProjectController}/{@code FeatureFlagController}: project-scoped
 * ({@code /api/projects/{projectId}/environments}) for create/list, and
 * environment-scoped ({@code /api/environments/{environmentId}}) for
 * get/rename/delete. No class-level {@code @RequestMapping}, same reason
 * as {@code ProjectController} — two different route prefixes.
 * <p>
 * Environment-scoped endpoints resolve the owning organization via
 * {@code environment.getProject().getOrganization().getId()} (two
 * chained LAZY loads; relies on this project's default
 * {@code spring.jpa.open-in-view=true}, same as the existing {@code create}
 * handler's own comment on this). There's deliberately no
 * {@code organizationId}/{@code projectId} in these routes to go stale
 * against the fetched entity's real parents.
 * <p>
 * Local try/catch is kept (not migrated to {@code GlobalExceptionHandler}),
 * matching {@code ProjectController}'s own note on scope for this batch.
 * <p>
 * {@code create}/{@code rename}/{@code delete} require
 * {@code OWNER}/{@code ADMIN} (unchanged tier from the existing
 * {@code create}). {@code list}/{@code getById} are open to any org member.
 */
@RestController
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

    @PostMapping("/api/projects/{projectId}/environments")
    public ResponseEntity<EnvironmentResponse> create(@PathVariable UUID projectId,
                                                      @Valid @RequestBody CreateEnvironmentRequest request,
                                                      @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Project project = projectService.getActiveById(projectId);

            requireManageRole(project.getOrganization().getId(), principal);

            Environment environment = environmentService.create(project, request.name(), request.key(), principal.getUser());
            return ResponseEntity.status(HttpStatus.CREATED).body(EnvironmentResponse.from(environment));
        } catch (EntityNotFoundException projectNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException duplicateKey) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/api/projects/{projectId}/environments")
    public ResponseEntity<List<EnvironmentResponse>> listByProject(@PathVariable UUID projectId,
                                                                   @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Project project = projectService.getActiveById(projectId);
            requireMembership(project.getOrganization().getId(), principal);

            List<EnvironmentResponse> environments = environmentService.listActiveByProject(projectId).stream()
                    .map(EnvironmentResponse::from)
                    .toList();
            return ResponseEntity.ok(environments);
        } catch (EntityNotFoundException projectNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/api/environments/{environmentId}")
    public ResponseEntity<EnvironmentResponse> getById(@PathVariable UUID environmentId,
                                                       @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Environment environment = environmentService.getActiveById(environmentId);
            requireMembership(environment.getProject().getOrganization().getId(), principal);
            return ResponseEntity.ok(EnvironmentResponse.from(environment));
        } catch (EntityNotFoundException environmentNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/api/environments/{environmentId}")
    public ResponseEntity<EnvironmentResponse> rename(@PathVariable UUID environmentId,
                                                      @Valid @RequestBody UpdateEnvironmentRequest request,
                                                      @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Environment environment = environmentService.getActiveById(environmentId);
            requireManageRole(environment.getProject().getOrganization().getId(), principal);

            Environment renamed = environmentService.rename(environmentId, request.name(), principal.getUser());
            return ResponseEntity.ok(EnvironmentResponse.from(renamed));
        } catch (EntityNotFoundException environmentNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @DeleteMapping("/api/environments/{environmentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID environmentId,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Environment environment = environmentService.getActiveById(environmentId);
            requireManageRole(environment.getProject().getOrganization().getId(), principal);

            environmentService.softDelete(environmentId, principal.getUser());
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException environmentNotFound) {
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

    public record EnvironmentResponse(UUID id, UUID projectId, String name, EnvironmentType key) {
        static EnvironmentResponse from(Environment environment) {
            return new EnvironmentResponse(
                    environment.getId(), environment.getProject().getId(), environment.getName(), environment.getKey());
        }
    }
}