package com.prateek.featureflag.environment;

import com.prateek.featureflag.audit.AuditAction;
import com.prateek.featureflag.audit.AuditLogService;
import com.prateek.featureflag.project.Project;
import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.prateek.featureflag.audit.ResourceType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for {@link Environment}. Duplicate-key check reuses
 * {@code findByProjectIdAndKeyAndDeletedAtIsNull} — no new repository
 * method added, same reasoning as {@code ProjectService}.
 * <p>
 * State-changing operations are recorded via the existing
 * {@link AuditLogService}, following the same pattern as
 * {@code ProjectService}: {@code EnvironmentController.create} calls the
 * original 3-arg {@code create(Project, String, EnvironmentType)}, which has
 * no actor to attribute a log entry to, so it's left unchanged (unlogged).
 * A new 4-arg {@code create(..., User actor)} overload is added alongside it
 * that performs the same creation and also logs "environment created". Since
 * {@code AuditLog} requires an {@link com.prateek.featureflag.organization.Organization},
 * it's resolved via {@code project.getOrganization()}/
 * {@code environment.getProject().getOrganization()}. {@code rename} and
 * {@code softDelete} had no external callers at all, so an {@code actor}
 * parameter was added directly to each.
 */
@Service
@Transactional(readOnly = true)
public class EnvironmentService {

    private static final ResourceType ENTITY_TYPE = ResourceType.ENVIRONMENT;

    private final EnvironmentRepository environmentRepository;
    private final AuditLogService auditLogService;

    public EnvironmentService(EnvironmentRepository environmentRepository, AuditLogService auditLogService) {
        this.environmentRepository = environmentRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Environment create(Project project, String name, EnvironmentType key) {
        if (environmentRepository.findByProjectIdAndKeyAndDeletedAtIsNull(project.getId(), key).isPresent()) {
            throw new IllegalStateException(
                    "Environment key already exists for this project: " + key);
        }
        return environmentRepository.save(new Environment(project, name, key));
    }

    /**
     * Same creation logic as {@link #create(Project, String, EnvironmentType)},
     * plus an audit log entry attributed to {@code actor}.
     */
    @Transactional
    public Environment create(Project project, String name, EnvironmentType key, User actor) {
        Environment environment = create(project, name, key);
        auditLogService.record(project.getOrganization(), actor, AuditAction.ENVIRONMENT_CREATED, ENTITY_TYPE,
                environment.getId(), null);
        return environment;
    }

    public Environment getActiveById(UUID id) {
        return environmentRepository.findById(id)
                .filter(environment -> !environment.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException("Environment not found: " + id));
    }

    public List<Environment> listActiveByProject(UUID projectId) {
        return environmentRepository.findByProjectIdAndDeletedAtIsNull(projectId);
    }

    @Transactional
    public Environment rename(UUID id, String newName, User actor) {
        Environment environment = getActiveById(id);
        environment.setName(newName);
        Environment saved = environmentRepository.save(environment);
        auditLogService.record(saved.getProject().getOrganization(), actor, AuditAction.ENVIRONMENT_RENAMED,
                ENTITY_TYPE, saved.getId(), null);
        return saved;
    }

    @Transactional
    public void softDelete(UUID id, User actor) {
        Environment environment = getActiveById(id);
        environment.setDeletedAt(Instant.now());
        Environment saved = environmentRepository.save(environment);
        auditLogService.record(saved.getProject().getOrganization(), actor, AuditAction.ENVIRONMENT_DELETED,
                ENTITY_TYPE, saved.getId(), null);
    }
}