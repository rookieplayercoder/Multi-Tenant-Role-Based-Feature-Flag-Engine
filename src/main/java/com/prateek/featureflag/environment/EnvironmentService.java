package com.prateek.featureflag.environment;

import com.prateek.featureflag.project.Project;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for {@link Environment}. Duplicate-key check reuses
 * {@code findByProjectIdAndKeyAndDeletedAtIsNull} — no new repository
 * method added, same reasoning as {@code ProjectService}.
 */
@Service
@Transactional(readOnly = true)
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;

    public EnvironmentService(EnvironmentRepository environmentRepository) {
        this.environmentRepository = environmentRepository;
    }

    @Transactional
    public Environment create(Project project, String name, EnvironmentType key) {
        if (environmentRepository.findByProjectIdAndKeyAndDeletedAtIsNull(project.getId(), key).isPresent()) {
            throw new IllegalStateException(
                    "Environment key already exists for this project: " + key);
        }
        return environmentRepository.save(new Environment(project, name, key));
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
    public Environment rename(UUID id, String newName) {
        Environment environment = getActiveById(id);
        environment.setName(newName);
        return environmentRepository.save(environment);
    }

    @Transactional
    public void softDelete(UUID id) {
        Environment environment = getActiveById(id);
        environment.setDeletedAt(Instant.now());
        environmentRepository.save(environment);
    }
}
