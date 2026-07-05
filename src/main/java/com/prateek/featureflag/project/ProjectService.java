package com.prateek.featureflag.project;

import com.prateek.featureflag.audit.AuditLogService;
import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for {@link Project}. {@link ProjectRepository} exposes no
 * {@code existsBy...} method, so the duplicate-key check reuses the
 * existing {@code findByOrganizationIdAndKeyAndDeletedAtIsNull} finder
 * rather than adding a new repository method (repositories are frozen
 * this batch).
 * <p>
 * State-changing operations are recorded via the existing
 * {@link AuditLogService}. {@code ProjectController.create} calls the
 * original 3-arg {@code create(Organization, String, String)}, which has no
 * actor to attribute a log entry to, so that overload is left exactly as it
 * was (unlogged) to avoid changing {@code ProjectController}. A new 4-arg
 * {@code create(..., User actor)} overload is added alongside it, which
 * performs the same creation and also logs "project created" — ready for a
 * future caller that has an actor to pass. {@code rename} and
 * {@code softDelete} had no external callers at all, so an {@code actor}
 * parameter was added directly to each, matching the pattern used for
 * {@code OrganizationService}. {@code updateKey} is untouched — it wasn't
 * asked for and reusing the same reasoning would only obscure the diff.
 */
@Service
@Transactional(readOnly = true)
public class ProjectService {

    private static final String ENTITY_TYPE = "Project";

    private final ProjectRepository projectRepository;
    private final AuditLogService auditLogService;

    public ProjectService(ProjectRepository projectRepository, AuditLogService auditLogService) {
        this.projectRepository = projectRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Project create(Organization organization, String name, String key) {
        if (projectRepository.findByOrganizationIdAndKeyAndDeletedAtIsNull(organization.getId(), key).isPresent()) {
            throw new IllegalStateException(
                    "Project key already in use in this organization: " + key);
        }
        return projectRepository.save(new Project(organization, name, key));
    }

    /**
     * Same creation logic as {@link #create(Organization, String, String)},
     * plus an audit log entry attributed to {@code actor}.
     */
    @Transactional
    public Project create(Organization organization, String name, String key, User actor) {
        Project project = create(organization, name, key);
        auditLogService.record(organization, actor, "project.created", ENTITY_TYPE, project.getId(), null);
        return project;
    }

    public Project getActiveById(UUID id) {
        return projectRepository.findById(id)
                .filter(project -> !project.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException("Project not found: " + id));
    }

    public List<Project> listActiveByOrganization(UUID organizationId) {
        return projectRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId);
    }

    @Transactional
    public Project rename(UUID id, String newName, User actor) {
        Project project = getActiveById(id);
        project.setName(newName);
        Project saved = projectRepository.save(project);
        auditLogService.record(saved.getOrganization(), actor, "project.renamed", ENTITY_TYPE, saved.getId(), null);
        return saved;
    }

    @Transactional
    public Project updateKey(UUID id, String newKey) {
        Project project = getActiveById(id);
        if (!project.getKey().equals(newKey)
                && projectRepository.findByOrganizationIdAndKeyAndDeletedAtIsNull(
                project.getOrganization().getId(), newKey).isPresent()) {
            throw new IllegalStateException("Project key already in use in this organization: " + newKey);
        }
        project.setKey(newKey);
        return projectRepository.save(project);
    }

    @Transactional
    public void softDelete(UUID id, User actor) {
        Project project = getActiveById(id);
        project.setDeletedAt(Instant.now());
        Project saved = projectRepository.save(project);
        auditLogService.record(saved.getOrganization(), actor, "project.deleted", ENTITY_TYPE, saved.getId(), null);
    }
}