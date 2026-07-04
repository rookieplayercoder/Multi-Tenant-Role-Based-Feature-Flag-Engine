package com.prateek.featureflag.environment;

import com.prateek.featureflag.flag.FeatureFlag;
import com.prateek.featureflag.environment.EnvironmentTypeConverter;
import com.prateek.featureflag.project.Project;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * An Environment (dev/test/staging/prod) belongs to exactly one
 * {@link Project} and scopes the state of every {@link FeatureFlag} within
 * it — the same conceptual flag can be enabled in one environment and
 * disabled in another, which is why FeatureFlag FKs here rather than to
 * Project directly. Maps exactly to the {@code environments} table in
 * V1__initial_schema.sql.
 */
@Entity
@Table(
        name = "environments",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_environments_project_key",
                columnNames = {"project_id", "key"}
        )
)
public class Environment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Owning side of the FK. LAZY explicitly set to override the JPA-spec
     * EAGER default for @ManyToOne.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @NotBlank
    @Size(max = 100)
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Backed by {@link EnvironmentTypeConverter}, which lowercases on write
     * and uppercases on read to reconcile Java enum convention with the
     * DB's lowercase CHECK constraint.
     */
    @NotNull
    @Column(name = "key", nullable = false, length = 50)
    private EnvironmentType key;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Inverse side of Environment <-> FeatureFlag. FeatureFlag owns the FK
     * (environment_id). PERSIST/MERGE only, same reasoning as prior
     * batches — explicit deletion stays a service-layer concern.
     */
    @OneToMany(mappedBy = "environment", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private Set<FeatureFlag> featureFlags = new HashSet<>();

    protected Environment() {
    }

    public Environment(Project project, String name, EnvironmentType key) {
        this.project = project;
        this.name = name;
        this.key = key;
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EnvironmentType getKey() {
        return key;
    }

    public void setKey(EnvironmentType key) {
        this.key = key;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Set<FeatureFlag> getFeatureFlags() {
        return Set.copyOf(featureFlags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Environment other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Environment{id=%s, name='%s', key=%s}".formatted(id, name, key);
    }
}
