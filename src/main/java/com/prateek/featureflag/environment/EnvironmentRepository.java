package com.prateek.featureflag.environment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {

    Optional<Environment> findByProjectIdAndKeyAndDeletedAtIsNull(UUID projectId, EnvironmentType key);

    List<Environment> findByProjectIdAndDeletedAtIsNull(UUID projectId);
}
