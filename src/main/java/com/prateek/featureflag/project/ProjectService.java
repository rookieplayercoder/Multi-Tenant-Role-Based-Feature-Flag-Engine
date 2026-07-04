package com.prateek.featureflag.project;

import com.prateek.featureflag.organization.Organization;
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
 */
@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public Project create(Organization organization, String name, String key) {
        if (projectRepository.findByOrganizationIdAndKeyAndDeletedAtIsNull(organization.getId(), key).isPresent()) {
            throw new IllegalStateException(
                    "Project key already in use in this organization: " + key);
        }
        return projectRepository.save(new Project(organization, name, key));
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
    public Project rename(UUID id, String newName) {
        Project project = getActiveById(id);
        project.setName(newName);
        return projectRepository.save(project);
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
    public void softDelete(UUID id) {
        Project project = getActiveById(id);
        project.setDeletedAt(Instant.now());
        projectRepository.save(project);
    }
}
