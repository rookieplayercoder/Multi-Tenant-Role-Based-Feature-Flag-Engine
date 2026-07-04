package com.prateek.featureflag.project;

import com.prateek.featureflag.environment.Environment;
import com.prateek.featureflag.organization.Organization;
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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A Project belongs to exactly one {@link Organization} and owns one or more
 * {@link Environment}s. Maps exactly to the {@code projects} table in
 * V1__initial_schema.sql.
 * <p>
 * The relationship to {@link Organization} is intentionally unidirectional —
 * {@code Organization} is not structurally modified for this relationship,
 * so there is no inverse {@code @OneToMany<Project>} collection there.
 * Fetching "all projects for an org" is a repository query concern, not an
 * entity-graph requirement.
 * <p>
 * {@code deleted_at} is a plain nullable column (no automatic Hibernate
 * filtering) — consistent with the Organization/User decision in Batch 1.
 */
@Entity
@Table(
        name = "projects",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_projects_org_key",
                columnNames = {"organization_id", "key"}
        )
)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Owning side of the FK. Named per the "organization" spelling
     * convention adopted going forward; the referenced type remains
     * {@link Organization} since that class name mirrors the locked
     * schema/table name. LAZY explicitly set to override the JPA-spec
     * EAGER default for @ManyToOne.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[a-z0-9-]+$", message = "key must be lowercase alphanumeric with hyphens only")
    @Column(name = "key", nullable = false)
    private String key;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Inverse side of Project <-> Environment. Environment owns the FK
     * (project_id). PERSIST/MERGE only — no REMOVE/orphanRemoval, so
     * environment deletion stays an explicit, auditable service action.
     */
    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private Set<Environment> environments = new HashSet<>();

    protected Project() {
    }

    public Project(Organization organization, String name, String key) {
        this.organization = organization;
        this.name = name;
        this.key = key;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
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

    public Set<Environment> getEnvironments() {
        return Set.copyOf(environments);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Project other)) {
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
        return "Project{id=%s, name='%s', key='%s'}".formatted(id, name, key);
    }
}
