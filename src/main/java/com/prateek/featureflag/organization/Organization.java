package com.prateek.featureflag.organization;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant root entity. Every tenant-owned row in the system traces back to an Organization.
 * <p>
 * Maps exactly to the {@code organizations} table in V1__initial_schema.sql —
 * no additional columns beyond what that migration defines.
 * <p>
 * Class name intentionally kept as {@code Organization} (American spelling)
 * since it mirrors the locked schema/table name. Package name and newly
 * introduced method/field names elsewhere (e.g. Project.getOrganization())
 * use the British "organization" spelling per project convention going
 * forward — this deliberate mismatch is documented rather than silently
 * resolved.
 * <p>
 * {@code deleted_at} is a plain nullable column here (no Hibernate-level
 * automatic filtering). Until a service layer exists, callers are
 * responsible for checking {@link #isDeleted()} and for setting
 * {@link #setDeletedAt(Instant)} explicitly rather than relying on any
 * transparent interception of delete/query operations.
 */
@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[a-z0-9-]+$", message = "slug must be lowercase alphanumeric with hyphens only")
    @Column(name = "slug", nullable = false)
    private String slug;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Inverse side of the Organization <-> Member relationship. Member owns
     * the FK (organization_id). PERSIST/MERGE cascade for convenience when
     * building an aggregate; REMOVE is intentionally excluded so membership
     * deletion always goes through an explicit, auditable service call
     * rather than an implicit cascading/orphan delete.
     */
    @OneToMany(mappedBy = "organization", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private Set<Member> members = new HashSet<>();

    /** Required by JPA. Not for application use. */
    protected Organization() {
    }

    public Organization(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
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

    /**
     * Unmodifiable view — callers must go through domain methods (added in a
     * later module) rather than mutating the collection directly, to keep
     * both sides of the bidirectional relationship in sync.
     */
    public Set<Member> getMembers() {
        return Set.copyOf(members);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Organization other)) {
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
        return "Organization{id=%s, name='%s', slug='%s'}".formatted(id, name, slug);
    }
}
